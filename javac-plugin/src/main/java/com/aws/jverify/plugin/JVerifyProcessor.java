package com.aws.jverify.plugin;

import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import java.util.Collections;
import java.util.Set;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class JVerifyProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.emptySet();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        JavacTask task = JavacTask.instance(processingEnv);

        task.addTaskListener(
                new TaskListener() {
                    @Override
                    public void finished(TaskEvent e) {

                        boolean resolutionInformationBecameAvailable =
                                e.getKind() == TaskEvent.Kind.ANALYZE;
                        if (resolutionInformationBecameAvailable) {
                            JCTree.JCCompilationUnit cu =
                                    (JCTree.JCCompilationUnit) e.getCompilationUnit();

                            Context context =
                                    ((JavacProcessingEnvironment) processingEnv).getContext();

                            var visitor = new RemoveJVerifyVisitor(context);
                            for (var rootElement : cu.getTypeDecls()) {
                                if (rootElement instanceof JCTree.JCClassDecl classDecl) {
                                    visitor.visitClassDef(classDecl);
                                }
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
