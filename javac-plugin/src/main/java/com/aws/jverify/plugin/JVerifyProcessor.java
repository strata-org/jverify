package com.aws.jverify.plugin;

import com.aws.jverify.verifier.JavaToDafnyCompiler;
import com.google.auto.service.AutoService;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import sun.misc.Unsafe;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Set;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class JVerifyProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.emptySet();
    }
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        JavacProcessingEnvironment javacProcessingEnvironment = getJavacProcessingEnvironment(processingEnv);
        if (javacProcessingEnvironment == null) {
            throw new RuntimeException(processingEnv.getClass().toString());
        }

        JavacTask task = JavacTask.instance(javacProcessingEnvironment);

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

    public JavacProcessingEnvironment getJavacProcessingEnvironment(Object procEnv) {
        addModuleOpens();
        if (procEnv instanceof JavacProcessingEnvironment) return (JavacProcessingEnvironment) procEnv;

        // try to find a "delegate" field in the object, and use this to try to obtain a JavacProcessingEnvironment
        for (Class<?> procEnvClass = procEnv.getClass(); procEnvClass != null; procEnvClass = procEnvClass.getSuperclass()) {
            Object delegate = tryGetDelegateField(procEnvClass, procEnv);
            if (delegate == null) delegate = tryGetProxyDelegateToField(procEnvClass, procEnv);
            if (delegate == null) delegate = tryGetProcessingEnvField(procEnvClass, procEnv);

            if (delegate != null) return getJavacProcessingEnvironment(delegate);
            // delegate field was not found, try on superclass
        }

        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                "Can't get the delegate of the gradle IncrementalProcessingEnvironment. Lombok won't work.");
        return null;
    }

    private Object tryGetDelegateField(Class<?> delegateClass, Object instance) {
        try {
            return getField(delegateClass, "delegate").get(instance);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * IntelliJ IDEA >= 2020.3
     */
    private Object tryGetProxyDelegateToField(Class<?> delegateClass, Object instance) {
        try {
            InvocationHandler handler = Proxy.getInvocationHandler(instance);
            return getField(handler.getClass(), "val$delegateTo").get(handler);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Kotlin incremental processing
     */
    private Object tryGetProcessingEnvField(Class<?> delegateClass, Object instance) {
        try {
            return getField(delegateClass, "processingEnv").get(instance);
        } catch (Exception e) {
            return null;
        }
    }

    public static Field getField(Class<?> c, String fName) throws NoSuchFieldException {
        Field f = null;
        Class<?> d = c;
        while (d != null) {
            try {
                f = d.getDeclaredField(fName);
                break;
            } catch (NoSuchFieldException e) {}
            d = d.getSuperclass();
        }
        if (f == null) throw new NoSuchFieldException(c.getName() + " :: " + fName);

        return setAccessible(f);
    }

    public static <T extends AccessibleObject> T setAccessible(T accessor) {
        accessor.setAccessible(true);
        return accessor;
    }

    private void addModuleOpens() {
        Unsafe unsafe = getUnsafe();
        Object jdkCompilerModule = getJdkCompilerModule();
        Object ownModule = getOwnModule();
        String[] allPkgs = {
                "com.sun.tools.javac.code",
                "com.sun.tools.javac.comp",
                "com.sun.tools.javac.file",
                "com.sun.tools.javac.main",
                "com.sun.tools.javac.model",
                "com.sun.tools.javac.parser",
                "com.sun.tools.javac.processing",
                "com.sun.tools.javac.tree",
                "com.sun.tools.javac.util",
                "com.sun.tools.javac.jvm",
        };

        try {
            Method m = Module.class.getDeclaredMethod("implAddOpens", String.class, Module.class);
            long firstFieldOffset = getFirstFieldOffset(unsafe);
            unsafe.putBooleanVolatile(m, firstFieldOffset, true);
            for (String p : allPkgs) m.invoke(jdkCompilerModule, p, ownModule);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object getJdkCompilerModule() {
		return  ModuleLayer.boot().findModule("jdk.compiler").get();
    }

    private static Object getOwnModule() {
        try {
            Method m = Class.class.getMethod("getModule");
            m.setAccessible(true);
            return m.invoke(JavaToDafnyCompiler.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static long getFirstFieldOffset(Unsafe unsafe) {
        try {
            return unsafe.objectFieldOffset(Parent.class.getDeclaredField("first"));
        } catch (NoSuchFieldException e) {
            // can't happen.
            throw new RuntimeException(e);
        } catch (SecurityException e) {
            // can't happen
            throw new RuntimeException(e);
        }
    }



    private static Unsafe getUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return true;
    }    
}

