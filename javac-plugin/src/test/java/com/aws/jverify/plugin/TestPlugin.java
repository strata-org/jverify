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
    public class TestPlugin {

        @Test
        public void checkCallsToVerifyAreNotPresentInClassfile() throws IOException {
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

            for(var generatedFile : compilation.generatedFiles()) {
                inspectConstantPool(generatedFile);
            }

            CompilationSubject.assertThat(compilation).succeeded();

            checkGeneratedClassStillWorks(compilation);
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
            for(var entry : cm.constantPool()) {
                if (entry instanceof ClassEntry classEntry) {
                    if (classEntry.asInternalName().equals("com/aws/jverify/JVerify")) {
                        Assertions.fail("No calls to JVerify allowed in class file");
                    }
                }
            }
        }

        private void checkGeneratedClassStillWorks(Compilation compilation) {
            try {
                Path tempDir = saveGeneratedClassesToTempDirectory(compilation);

                URLClassLoader classLoader = new URLClassLoader(
                        new URL[] { tempDir.toUri().toURL() },
                        getClass().getClassLoader()
                );
                Class<?> modifyMeClass = classLoader.loadClass("com.example.ModifyMe");
                Object instance = modifyMeClass.getDeclaredConstructor().newInstance();
                Method barMethod = modifyMeClass.getDeclaredMethod("Bar");
                var result = barMethod.invoke(instance);
                Assertions.assertEquals(3, result);

                classLoader.close();
                deleteDirectory(tempDir.toFile());

            } catch (Exception e) {
                Assertions.fail(STR."Failed to execute generated class: \{e.getMessage()}");
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
            String uri = fileObject.toUri().toString();
            int classIndex = uri.lastIndexOf(".class");
            if (classIndex != -1) {
                        
                // Extract class name from URI
                String path = uri;
                path = path.substring(0, classIndex);
                if (path.startsWith("mem:")) {
                    path = path.substring(4);
                }

                // Handle path format in compilation output
                if (path.contains("/CLASS_OUTPUT/")) {
                    int startIndex = path.indexOf("/CLASS_OUTPUT/") + "/CLASS_OUTPUT/".length();
                    path = path.substring(startIndex);
                    return path.replace('/', '.');
                }
            }
            return null;
        }

        private Path createPathFromClassName(Path baseDir, String className) {
            String filePath = className.replace('.', '/') + ".class";
            return baseDir.resolve(filePath);
        }

        private void deleteDirectory(File directory) {
            if (directory.exists()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            deleteDirectory(file);
                        } else {
                            file.delete();
                        }
                    }
                }
                directory.delete();
            }
        }
}
