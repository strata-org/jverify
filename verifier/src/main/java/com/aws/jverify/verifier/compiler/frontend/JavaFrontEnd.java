package com.aws.jverify.verifier.compiler.frontend;

import com.aws.jverify.verifier.VerifierOptions;
import com.aws.jverify.verifier.compiler.simplifications.LambdaToAnonymousClassCompiler;
import com.aws.jverify.verifier.compiler.*;
import com.aws.jverify.verifier.compiler.simplifications.MoveStaticMethodsToStaticType;
import com.aws.jverify.verifier.compiler.simplifications.ExternalContractCompiler;
import com.aws.jverify.verifier.compiler.simplifications.MethodReferenceToLambdaCompiler;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.main.Arguments;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Position;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class JavaFrontEnd {
    public final Context context;
    Reporter reporter;

    public JavaFrontEnd(JavaToDafnyCompiler javaToDafnyCompiler) {
        this.context = javaToDafnyCompiler.context;
        reporter = Reporter.instance(context);
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
         *
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
         *
         * The first half mostly adds information to the tree, like resolution,
         * whereas the second half starts to be more destructive,
         * lowering higher-level features to lower-level ones.
         * Some of the latter are helpful, but in some cases the target language (Dafny)
         * supports features that JVM bytecode doesn't, so the phases don't help.
         *
         * Currently, we apply 0 through 5,
         * skip 6 and 7 as they remove features Dafny supports directly (generics and patterns),
         * but then apply 8 in order to rewrite lambda expressions and method references,
         * and 9 to rewrite features such as nested classes and autoboxing.
         * 10 actually generates JVM bytecode so we will likely never apply it.
         *
         * Because we have specification and proof code that use features like lambdas
         * for different purposes, we also apply our own phases before and after UNLAMBDA and LOWER
         * in order to temporarily remove/rewrite code we don't want rewritten and then restore it.
         * Therefore, our current pipeline looks like this:
         *
         * INIT(0),
         * PARSE(1),
         * ENTER(2),
         * PROCESS(3),
         * ATTR(4),
         * FLOW(5),
         * Method references to lambdas,
         * Lambda to local classes,
         * SUSPEND,
         * LOWER(9),
         * UNSUSPEND
         *
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
                    var contractCompiler = new ExternalContractCompiler(context);
                    units.addAll(
                            staticMover.translate(
                            contractCompiler.apply(
                                unsuspend(lower(suspend(
                                        unlambda(
                                                compiler.flow(compiler.attribute(todo))
                    )))))));
                }
            }
        });
        // Applies the Java to Java part of our pipeline
        compiler.compile(files, List.of(), null, List.of());

        var javaFrontendDiagnostics = (DiagnosticCollector<? extends JavaFileObject>)context.get(DiagnosticListener.class);
        
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

    private Queue<Env<AttrContext>> unlambda(Queue<Env<AttrContext>> envs) {;

        // Note JavaCompiler.desugar has some additional logic to
        // scan for classes that have lambdas first,
        // to not waste time with these traversals.
        // We could do the same here to save time in the future.
        for (Env<AttrContext> env : envs) {
            env.tree = new MethodReferenceToLambdaCompiler(context).translate(env.tree);
            env.tree = new LambdaToAnonymousClassCompiler(env.toplevel, context).translate(env.tree);
        }
        return envs;
    }

    // Phase to hide/rewrite higher level features such as switches
    // so future phases don't rewrite them.
    private Queue<Env<AttrContext>> suspend(Queue<Env<AttrContext>> envs) {
        var suspenders = Suspenders.instance(context);
        Log log = Log.instance(context);
        for (Env<AttrContext> env: envs) {
            log.useSource(env.toplevel.sourcefile);
            env.tree = suspenders.suspend(env.tree);
        }
        return envs;
    }

    // Phase to undo the effects of suspend().
    private Set<JCTree.JCCompilationUnit> unsuspend(Set<JCTree.JCCompilationUnit> units) {
        var hider = Suspenders.instance(context);
        units.forEach(topLevel -> {
            topLevel.defs = topLevel.defs.map(hider::unsuspend);
        });
        return units;
    }

    private Set<JCTree.JCCompilationUnit> lower(Queue<Env<AttrContext>> envs) {
        var localMake = TreeMaker.instance(context).at(Position.NOPOS);
        var lower = Lower.instance(context);
        var log = Log.instance(context);
        var index = JVerifyIndex.instance(context);

        Map<JCTree.JCCompilationUnit, com.sun.tools.javac.util.List<JCTree>> newDecls = new HashMap<>();
        for (Env<AttrContext> env : envs) {
            log.useSource(env.toplevel.sourcefile);
            com.sun.tools.javac.util.List<JCTree> classes = lower.translateTopLevelClass(env, env.tree, localMake);

            for (JCTree clazz : classes) {
                index.index(env, clazz);
            }

            var decls = newDecls.getOrDefault(env.toplevel, com.sun.tools.javac.util.List.nil());
            newDecls.put(env.toplevel, decls.appendList(classes));
        }
        for(var entry : newDecls.entrySet()) {
            entry.getKey().defs = entry.getValue();
        }
        return new HashSet<>(newDecls.keySet());
    }
}
