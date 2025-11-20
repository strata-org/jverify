package com.aws.jverify.verifier.compiler.frontend;

import com.aws.jverify.common.Common;
import com.aws.jverify.generated.*;
import com.aws.jverify.verifier.SourceFile;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.DiagnosticPositionFromDiagnostic;
import com.aws.jverify.verifier.compiler.Reporter;
import com.aws.jverify.verifier.compiler.dafnygenerator.DafnyGenerator;
import com.aws.jverify.verifier.compiler.simplifications.*;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.main.Arguments;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Position;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class JavaToDafnyCompiler {
    public final Context context;
    private final Reporter reporter;
    private final Enter enter;

    private final DafnyGenerator dafnyGenerator;
    public Set<SourceFile> builtinSources = new HashSet<>();
    public static final String builtinFile = "/builtin-contracts.java";
    public static final String objectFile = "/object-contract.java";

    public JavaToDafnyCompiler(Context context) {
        this.context = context;

        JavacFileManager.preRegister(context);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        context.put(DiagnosticListener.class, diagnostics);
        context.put(JavaToDafnyCompiler.class, this);

        reporter = Reporter.instance(context);
        enter = Enter.instance(context);
        this.dafnyGenerator = DafnyGenerator.getGenerator(context);
    }

    public @Nullable FilesContainer analyzeJavaCode(VerifierOptions options, java.util.List<JavaFileObject> files) {
        if (options.includeBuiltinContracts()) {
            var builtinSource = new SourceFile(builtinFile, Common.getResourceFile(getClass(), builtinFile));
            files.add(builtinSource);
            builtinSources.add(builtinSource);
        }
        var contractSource = new SourceFile(objectFile, Common.getResourceFile(getClass(), objectFile));
        builtinSources.add(contractSource);
        files.add(contractSource);

        Set<JCTree.JCCompilationUnit> parsedSet = parseResolveAndDesugarJava(options, files);
        if (parsedSet == null) {
            return new FilesContainer(List.of());
        }

        var parsed = new ArrayList<>(parsedSet);
        var libraries = parsed.stream().filter(u -> builtinSources.contains(u.getSourceFile())).collect(Collectors.toSet());

        return dafnyGenerator.generateDafny(parsed, libraries);
    }

    public boolean isLibrary(JCTree.JCCompilationUnit compilationUnit) {
        return builtinSources.contains(compilationUnit.getSourceFile());
    }

    public boolean isLibrary(URI sourceURI) {
        return builtinSources.stream()
                .anyMatch( file -> file.toUri().equals(sourceURI));
    }


    /**
     * Applies a subset of the javac compilation pipeline, to parse,
     * resolve, and partially rewrite some features away.
     */
    public Set<JCTree.JCCompilationUnit> parseResolveAndDesugarJava(VerifierOptions options, List<JavaFileObject> files) {
        // don't assume the argument is modifiable
        files = new ArrayList<>(files);

        for(var extraPath : options.extraClassPathEntries()) {
            if (!Files.exists(extraPath.toAbsolutePath())) {
                throw new IllegalArgumentException("Could not find file: " + extraPath);
            }
        }
        var classpathEntries = new ArrayList<Path>(options.extraClassPathEntries());
        var classpath = classpathEntries.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        var javacOptions = List.of("-cp", classpath);

        JavaCompiler compiler = JavaCompiler.instance(context);
        Arguments args = Arguments.instance(context);
        args.init("javac", javacOptions, List.of(), files);

        /**
         * The javac phases are as follows (copied from CompileStates.CompileState):
         * <p>
         * INIT(0),
         * PARSE(1),
         * ENTER(2),
         * PROCESS(3),
         * ATTR(4),
         * FLOW(5),
         * TRANSTYPES(6),
         * TRANSPATTERNS(7),
         * UNLAMBDA(8),
         * LOWER(9),
         * GENERATE(10);
         * <p>
         * The first half mostly adds information to the tree, like resolution,
         * whereas the second half starts to be more destructive,
         * lowering higher-level features to lower-level ones.
         * Some of the latter are helpful, but in some cases the target language (Dafny)
         * supports features that JVM bytecode doesn't, so the phases don't help.
         * <p>
         * Currently, we apply 0 through 5,
         * skip 6 and 7 as they remove features Dafny supports directly (generics and patterns),
         * but then apply 8 in order to rewrite lambda expressions and method references,
         * and 9 to rewrite features such as nested classes and autoboxing.
         * 10 actually generates JVM bytecode so we will likely never apply it.
         * <p>
         * Because we have specification and proof code that use features like lambdas
         * for different purposes, we also apply our own phases before and after UNLAMBDA and LOWER
         * in order to temporarily remove/rewrite code we don't want rewritten and then restore it.
         * Therefore, our current pipeline looks like this:
         * <p>
         * INIT(0),
         * PARSE(1),
         * ENTER(2),
         * PROCESS(3),
         * ATTR(4),
         * FLOW(5),
         * JVerify specific: MethodReferenceToLambdaCompiler,
         * JVerify specific: LambdaToAnonymousClassCompiler,
         * JVerify specific: SUSPEND,
         * JVerify customized: LOWER(9),
         * JVerify specific: UNSUSPEND
         * JVerify specific: ExternalContractCompiler
         * JVerify specific: MoveStaticMethodsToStaticType
         * <p>
         * For practical reasons we also have to stop the normal flow of the JavaCompiler
         * after 3 in order to get a reference to the set of compilation targets
         * (via a TaskListener and a configuration to stop the pipeline early,
         * and the Todo instance in the context).
         */
        compiler.shouldStopPolicyIfNoError = CompileStates.CompileState.PROCESS;
        Set<JCTree.JCCompilationUnit> units = new HashSet<>();
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        mtl.add(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                TaskListener.super.finished(e);
                
                // Wait for the last event sent, after all compilation is complete
                // (which will be just phase 0 through 3 because of the shouldStopPolicyIfNoError setting)
                if (e.getKind() == TaskEvent.Kind.COMPILATION) {
                    @SuppressWarnings("unchecked") var javaFrontendDiagnostics = (DiagnosticCollector<? extends JavaFileObject>)context.get(DiagnosticListener.class);
                    if (!javaFrontendDiagnostics.getDiagnostics().isEmpty()) {
                        return;
                    }
                    // The earlier phases leave the queue of classes to process
                    // in this instance.
                    // JavaCompile.compile() would normally make a call equivalent to
                    // generate(desugar(flow(attribute(todo)))),
                    // where desugar() applied phases 6 through 9.
                    Todo todo = Todo.instance(context);

                    // The stop policy has to be moved back further
                    // or else the later phases become no-ops.
                    compiler.shouldStopPolicyIfNoError = CompileStates.CompileState.FLOW;

                    // Apply the second half of our pipeline as above (4 and onwards).
                    // See the implementation of JavaCompiler.compile() for similar lines,
                    // including the comment "these method calls must be chained to avoid memory leaks"

                    var staticMover = new MoveStaticMethodsToStaticType(context);
                    var externalContractCompiler = new ExternalContractCompiler(context);
                    var missingContractCompiler = new MissingContractCompiler(context);
                    var newMethodContractCompiler = MethodOrLoopContractCompiler.instance(context);
                    var verifyAnnotationCompiler = VerifyAnnotationCompiler.instance(context);
                    var arrayCompiler = new ArrayCompiler(context);
                    units.addAll(
                            staticMover.translate(
                                    arrayCompiler.transform(
                                            unsuspend(lower(suspend(
                                                    missingContractCompiler.compile(
                                                            verifyAnnotationCompiler.transform(
                                                                    externalContractCompiler.apply(
                                                                            newMethodContractCompiler.transform(
                                                                                    unlambda(
                                                                                            toUnits(compiler.flow(compiler.attribute(todo)))
                                                                                    )))))))))));
                }
            }
        });
        // Applies the Java to Java part of our pipeline
        compiler.compile(files, java.util.List.of(), null, java.util.List.of());

        @SuppressWarnings("unchecked") var javaFrontendDiagnostics = (DiagnosticCollector<? extends JavaFileObject>)context.get(DiagnosticListener.class);

        var hasErrors = false;
        for (Diagnostic<? extends JavaFileObject> diagnostic : javaFrontendDiagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                JCDiagnostic.DiagnosticPosition position = new DiagnosticPositionFromDiagnostic(diagnostic);
                reporter.diagnostics.report(reporter.diagnosticFactory.create(JCDiagnostic.DiagnosticType.ERROR,
                        new DiagnosticSource(diagnostic.getSource(), null), position, "javaError",
                        diagnostic.getMessage(Locale.ENGLISH)));

                hasErrors = true;
            }
        }

        return hasErrors ? null : units;
    }

    private List<JCTree.JCCompilationUnit> toUnits(Queue<Env<AttrContext>> envs) {
        var result = envs.stream().map(e -> e.toplevel).distinct().toList();
        JVerifyIndex index = JVerifyIndex.instance(context);
        for(var unit : result) {
            Env<AttrContext> env = enter.getTopLevelEnv(unit);
            for(var def : unit.defs) {
                index.index(env, def);
            }
        }
        return result;
    }

    private List<JCTree.JCCompilationUnit> unlambda(List<JCTree.JCCompilationUnit> envs) {
        // Note JavaCompiler.desugar has some additional logic to
        // scan for classes that have lambdas first,
        // to not waste time with these traversals.
        // We could do the same here to save time in the future.
        for (var env : envs) {
            new MethodReferenceToLambdaCompiler(context).translate(env);
            new LambdaToAnonymousClassCompiler(env, context).translate(env);
        }
        return envs;
    }

    // Phase to hide/rewrite higher level features such as switches
    // so future phases don't rewrite them.
    private java.util.List<JCTree.JCCompilationUnit> suspend(java.util.List<JCTree.JCCompilationUnit> envs) {
        var suspenders = Suspenders.instance(context);
        Log log = Log.instance(context);
        for (var env: envs) {
            log.useSource(env.sourcefile);
            suspenders.suspend(env);
        }
        return envs;
    }

    // Phase to undo the effects of suspend().
    private List<JCTree.JCCompilationUnit> unsuspend(List<JCTree.JCCompilationUnit> units) {
        var hider = Suspenders.instance(context);
        units.forEach(topLevel -> {
            topLevel.defs = topLevel.defs.map(hider::unsuspend);
        });
        return units;
    }

    private List<JCTree.JCCompilationUnit> lower(List<JCTree.JCCompilationUnit> envs) {
        var localMake = TreeMaker.instance(context).at(Position.NOPOS);
        var lower = Lower.instance(context);
        var log = Log.instance(context);
        var index = JVerifyIndex.instance(context);

        for (var env : envs) {
            log.useSource(env.sourcefile);
            Env<AttrContext> unitEnv = enter.getTopLevelEnv(env);
            var newDefs = com.sun.tools.javac.util.List.<JCTree>nil();
            for(var topLevel : env.defs) {
                if (topLevel instanceof JCTree.JCClassDecl) {
                    com.sun.tools.javac.util.List<JCTree> classes = lower.translateTopLevelClass(unitEnv, topLevel, localMake);
                    for (JCTree clazz : classes) {
                        index.index(unitEnv, clazz);
                    }
                    newDefs = newDefs.appendList(classes);
                } else {
                    newDefs = newDefs.append(topLevel);
                }
            }
            env.defs = newDefs;
        }
        return envs;
    }

}
