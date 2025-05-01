package com.aws.jverify.plugin;

import com.aws.jverify.JVerify;
import com.aws.jverify.common.Common;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.lang.classfile.*;

@SuppressWarnings("preview")
public class PluginTest {

    @Test
    public void checkPreconditionAtRuntimeTest() throws Exception {
        String source = Files.readString(Path.of("./src/test/java/com/aws/jverify/plugin/CheckPreconditionAtRuntimeTest.java"));
        String fullyQualifiedName = "com.aws.jverify.plugin.CheckPreconditionAtRuntimeTest";
        var sourceFile = JavaFileObjects.forSourceString(fullyQualifiedName, source);
        var jVerifyClass = URI.create(Common.getJarLocationForClass(JVerify.class));

        List<File> classPath = List.of(new File(jVerifyClass));
        Compilation compilation = Compiler.javac()
                .withProcessors(new JVerifyProcessor())
                .withClasspath(classPath)
                .compile(sourceFile);

        CompilationSubject.assertThat(compilation).succeeded();

        int exitCode = CompilationRunner.runGeneratedClassAndGetExitCode(compilation, classPath, fullyQualifiedName);
        Assertions.assertEquals(0, exitCode);
    }

    @Test
    public void runtimePreconditionExampleTest() throws IOException {
        String source = Files.readString(Path.of("../examples/src/test/java/com/aws/jverify/examples/RuntimePreconditionExample.java"));
        var sourceFile = JavaFileObjects.forSourceString("com.aws.verifier.examples.RuntimePreconditionExample", source);
        var jVerifyClass = URI.create(Common.getJarLocationForClass(JVerify.class));

        List<File> classPath = List.of(new File(jVerifyClass));
        Compilation compilation = Compiler.javac()
                .withProcessors(new JVerifyProcessor())
                .withClasspath(classPath)
                .compile(sourceFile);

        CompilationSubject.assertThat(compilation).succeeded();

        int exitCode = CompilationRunner.runGeneratedClassAndGetExitCode(compilation, classPath, "com.aws.jverify.examples.RuntimePreconditionExample");
        Assertions.assertEquals(0, exitCode);
    }

    @Test
    public void checkCallsToJVerifyAreNotPresentInClassfile() throws Exception {
        JavaFileObject source = JavaFileObjects.forSourceString(
                "com.example.ModifyMe",
                """
                        package com.example;
                        import com.aws.jverify.*;
                        import static com.aws.jverify.JVerify.*;
                        
                        public class ModifyMe {
                          public int Bar() {
                            precondition(false);
                            postcondition(false);
                            invariant(false);
                            return 3;
                          }
                        }"""
        );

        var jVerifyClass = URI.create(Common.getJarLocationForClass(JVerify.class));

        Compilation compilation = Compiler.javac()
                .withProcessors(new JVerifyProcessor())
                .withClasspath(List.of(new File(jVerifyClass)))
                .compile(source);

        for (var generatedFile : compilation.generatedFiles()) {
            inspectConstantPool(generatedFile);
        }

        CompilationSubject.assertThat(compilation).succeeded();

        var result = runMethodFromCompilation(compilation, "com.example.ModifyMe", "Bar");
        Assertions.assertEquals(3, result);
    }

    public void inspectConstantPool(JavaFileObject fileObject) throws IOException {
        InputStream inputStream = fileObject.openInputStream();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        byte[] classBytes = outputStream.toByteArray();
        ClassModel cm = ClassFile.of().parse(classBytes);
        for (var entry : cm.constantPool()) {
            if (entry instanceof ClassEntry classEntry) {
                if (classEntry.asInternalName().equals("com/aws/jverify/JVerify")) {
                    Assertions.fail("No calls to JVerify allowed in class file");
                }
            }
        }
    }

    private Object runMethodFromCompilation(Compilation compilation,
                                            String fullyQualifiedClassName,
                                            String methodName) throws Exception {
        Path tempDir = null;
        try {
            tempDir = saveGeneratedClassesToTempDirectory(compilation);

            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{tempDir.toUri().toURL()},
                    getClass().getClassLoader()
            )) {
                Class<?> clazz = classLoader.loadClass(fullyQualifiedClassName);
                Object instance = clazz.getDeclaredConstructor().newInstance();
                Method barMethod = clazz.getDeclaredMethod(methodName);
                return barMethod.invoke(instance);
            }
        } finally {
            if (tempDir != null) {
                CompilationRunner.deleteRecursively(tempDir);
            }
        }
    }

    private Path saveGeneratedClassesToTempDirectory(Compilation compilation) throws IOException {
        Path tempDir = Files.createTempDirectory("generated-classes");

        for (JavaFileObject generatedFile : compilation.generatedFiles()) {
            if (generatedFile.getKind() == JavaFileObject.Kind.CLASS) {
                String className = getClassName(generatedFile);
                if (className != null) {
                    Path classFilePath = createPathFromClassName(tempDir, className);
                    Files.createDirectories(classFilePath.getParent());

                    try (InputStream in = generatedFile.openInputStream()) {
                        Files.copy(in, classFilePath);
                    }
                }
            }
        }
        return tempDir;
    }

    private String getClassName(JavaFileObject fileObject) {
        // URI in one of our tests is mem:///CLASS_OUTPUT/com/example/ModifyMe.class
        String path = fileObject.toUri().getPath();
        return path.
                substring("/CLASS_OUTPUT/".length(), path.length() - ".class".length()).
                replace('/', '.');
    }

    private Path createPathFromClassName(Path baseDir, String className) {
        String filePath = className.replace('.', '/') + ".class";
        return baseDir.resolve(filePath);
    }
}
