package com.aws.jverify.plugin;

import com.aws.jverify.verifier.JavaToDafnyCompiler;
import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;


@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_23)
public class JVerifyProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.EMPTY_SET;
    }
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        JavacTask task = JavacTask.instance(processingEnv);

        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent e) {
                boolean resolutionInformationBecameAvailable = e.getKind() == TaskEvent.Kind.ANALYZE;
                if (resolutionInformationBecameAvailable) {
                    try {
                        JavaToDafnyCompiler.verify(processingEnv, e.getCompilationUnit());
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
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

