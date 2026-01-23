package com.aws.jverify.verifier.compiler.frontend;

import com.aws.jverify.common.Common;
import com.aws.jverify.verifier.Backend;
import com.aws.jverify.verifier.JarFileEntry;
import com.aws.jverify.verifier.SourceFile;
import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.DiagnosticPositionFromDiagnostic;
import com.aws.jverify.verifier.compiler.LoweredResult;
import com.aws.jverify.verifier.compiler.Reporter;
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
import com.sun.tools.javac.util.*;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class JavaLowerer {
    public final Context context;
    private final Reporter reporter;
    private final Enter enter;

    public Set<JavaFileObject> contractSources = new HashSet<>();
    public static final String objectFile = "/object-contract.java";

    public JavaLowerer(Context context) {
        this.context = context;
        context.put(JavaLowerer.class, this);

        JavacFileManager.preRegister(context);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        context.put(DiagnosticListener.class, diagnostics);

        reporter = Reporter.instance(context);
        enter = Enter.instance(context);
    }

    public @Nullable LoweredResult lowerJava(VerifierOptions options, List<JavaFileObject> files) {
        var contractSource = new SourceFile(objectFile, Common.getResourceFile(getClass(), objectFile));
        contractSources.add(contractSource);
        files.add(contractSource);

        for (var contractsPath : options.contractSourcePath()) {
            var resolvedContractsPath = options.workingDirectory().resolve(contractsPath);
            if (!Files.exists(resolvedContractsPath)) {
                throw new IllegalArgumentException("Could not find path: " + resolvedContractsPath);
            }
            if (resolvedContractsPath.toString().endsWith(".jar")) {
                try (var jarFile = new JarFile(resolvedContractsPath.toFile())) {
                    jarFile.stream()
                            .filter(jarEntry -> jarEntry.getName().endsWith(".java"))
                            .map(jarEntry ->
                                    new JarFileEntry(resolvedContractsPath, jarEntry)
                            )
                            .forEach(source -> {
                                files.add(source);
                                contractSources.add(source);
                            });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                try {
                    try (Stream<Path> stream = Files.walk(resolvedContractsPath)) {
                        stream.filter(path -> path.toString().endsWith(".java"))
                                .forEach(path -> {
                                    try {
                                        // Use the SourceFile constructor overload that uses "string://"
                                        // so that we can use a relative path
                                        // and simplify making portable test files.
                                        // Replace \ with / to ensure consistent names on Windows.
                                        var relativePath = resolvedContractsPath.relativize(path).toString().replace('\\', '/');
                                        SourceFile source = new SourceFile(relativePath, Files.readString(path));
                                        files.add(source);
                                        contractSources.add(source);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        Set<JCTree.JCCompilationUnit> loweredJava = parseResolveAndDesugarJava(options, files);
        if (loweredJava == null) {
            return new LoweredResult(new ArrayList<>(), Set.of());
        }

        var parsed = new ArrayList<>(loweredJava);
        var libraries = parsed.stream().filter(u -> contractSources.contains(u.getSourceFile())).collect(Collectors.toSet());
        return new LoweredResult(parsed, libraries);
    }

    public boolean isContractSource(JCTree.JCCompilationUnit compilationUnit) {
        return contractSources.contains(compilationUnit.getSourceFile());
    }

    public boolean isContractSource(URI sourceURI) {
        return contractSources.stream()
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
                throw new IllegalArgumentException("Could not find file: " + extraPath.toAbsolutePath());
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
                    if (javaFrontendDiagnostics.getDiagnostics().stream().
                            anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)) {
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

                    var remainingUnits = toUnits(compiler.flow(compiler.attribute(todo)));
                    
                    List<UnitsCompiler> phases = new ArrayList<>();
                    phases.add(JavaLowerer.this::insertFloatingPointCasts);
                    phases.add(JavaLowerer.this::unlambda);
                    phases.add(MethodOrLoopContractCompiler.instance(context)::transform);
                    phases.add(new ExternalContractCompiler(context)::transform);
                    if (options.positionFilter() != null && options.positionFilter().fileEnding() != null) {
                        phases.add(new DropUnreachableUnits(context)::transform);
                    }
                    phases.add(VerifyAnnotationCompiler.instance(context)::transform);
                    var backend = context.get(Backend.class);
                    var skipField = backend == Backend.Strata
                        ? (java.util.function.Predicate<com.sun.tools.javac.code.Symbol.VarSymbol>) vs -> vs.isStatic() && vs.getConstValue() instanceof Number
                        : (java.util.function.Predicate<com.sun.tools.javac.code.Symbol.VarSymbol>) vs -> false;
                    phases.add(new MissingContractCompiler(context, skipField)::transform);
                    phases.add(new IsAbstractCompiler(context)::transform);
                    phases.add(new PreconditionOfCompiler(context)::transform);
                    phases.add(us -> unsuspend(lower(suspend(us))));
                    phases.add(new ArrayCompiler(context)::transform);
                    phases.add(new MoveStaticMethodsToStaticType(context)::translate);

                    for (int i = 0; i < phases.size(); i++) {
                        var phase = phases.get(i);
                        remainingUnits = phase.transform(remainingUnits);
                    }
                    units.addAll(remainingUnits);
                }
            }
        });
        // Applies the Java to Java part of our pipeline
        compiler.compile(files, List.of(), null, List.of());

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
    
    interface UnitsCompiler {
        List<JCTree.JCCompilationUnit> transform(List<JCTree.JCCompilationUnit> units);
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

    private List<JCTree.JCCompilationUnit> insertFloatingPointCasts(List<JCTree.JCCompilationUnit> units) {
        var inserter = new FloatingPointCastInserter(context);
        for (var unit : units) {
            inserter.translate(unit);
        }
        return units;
    }

    // Phase to hide/rewrite higher level features such as switches
    // so future phases don't rewrite them.
    private List<JCTree.JCCompilationUnit> suspend(List<JCTree.JCCompilationUnit> envs) {
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
