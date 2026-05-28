package org.strata.jverify.contracts2jqwik;

import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import java.util.Collections;
import java.util.Set;

/**
 * Annotation processor that generates a jqwik {@code @Property} test method
 * for every method annotated with {@link org.strata.jverify.AsProperty}.
 *
 * <p>The translation is rule-based:</p>
 * <ul>
 *     <li>The original method's parameters become the generated method's parameters,
 *         carrying their {@code @ForAll} annotations.</li>
 *     <li>Each {@code precondition(P)} call is rewritten to {@code Assume.that(P);}.</li>
 *     <li>The {@code postcondition((T r) -> Q)} lambda is unwrapped: the lambda
 *         parameter becomes a local variable bound to the call to the original
 *         method, and the lambda body is returned.</li>
 *     <li>Postconditions taking a plain boolean expression (no return binding)
 *         are returned directly after invoking the original method (the result
 *         is discarded if there is no binding).</li>
 *     <li>Quantifiers ({@code forall} / {@code exists}) and {@code old(...)} are
 *         skipped with a SKIPPED comment, since they cannot be executed at runtime.</li>
 * </ul>
 *
 * <p>The processor hooks the {@link TaskEvent.Kind#ENTER} event so the synthesized
 * method participates in subsequent attribution and code generation.</p>
 */
@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class Contracts2JqwikProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.emptySet();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        JavacTask task = JavacTask.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();

        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                // Run after PARSE so the AST is available, but before ENTER completes
                // attribution. We mutate the class member list in-place; the synthesized
                // method is then attributed normally.
                if (e.getKind() != TaskEvent.Kind.PARSE) {
                    return;
                }
                JCTree.JCCompilationUnit cu = (JCTree.JCCompilationUnit) e.getCompilationUnit();
                AsPropertyExpander expander = new AsPropertyExpander(context, processingEnv);
                for (var typeDecl : cu.getTypeDecls()) {
                    if (typeDecl instanceof JCTree.JCClassDecl classDecl) {
                        expander.expandClass(classDecl);
                    }
                }
            }
        });
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return true;
    }
}
