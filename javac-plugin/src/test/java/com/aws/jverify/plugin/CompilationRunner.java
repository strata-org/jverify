package com.aws.jverify.plugin;

import com.google.errorprone.annotations.Var;
import com.google.testing.compile.Compilation;
import com.sun.source.doctree.ValueTree;

import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CompilationRunner {

    /**
     * Runs the main method of a generated class from a Compilation and returns the exit code.
     * This method launches the class in a separate JVM process to properly capture the exit code.
     *
     * @param compilation The compilation containing the generated class
     * @param className The fully qualified name of the class to run
     * @param args Arguments to pass to the main method
     * @return The exit code, or -1 if an error occurred
     */
    public static int runGeneratedClassAndGetExitCode(
            Compilation compilation, Iterable<File> classPath, String className, String... args) {

        // Create a temporary directory for the class files
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("generated-classes");
        } catch (IOException e) {
            System.err.println("Failed to create temp directory: " + e.getMessage());
            return -1;
        }

        try {
            // Write compiled class files to the temporary directory
            for (JavaFileObject generatedFile : compilation.generatedFiles()) {
                if (generatedFile.getKind() == JavaFileObject.Kind.CLASS) {
                    String binaryName = generatedFile.getName()
                            .substring(1, generatedFile.getName().length() - ".class".length())
                            .replace('/', '.');

                    // Create package directories
                    int lastDotIndex = binaryName.lastIndexOf('.');
                    if (lastDotIndex > 0) {
                        String packagePath = binaryName.substring(0, lastDotIndex).replace('.', '/');
                        Path packageDir = tempDir.resolve(packagePath);
                        Files.createDirectories(packageDir);
                    }

                    // Write class file
                    String classFileName = binaryName.replace('.', '/') + ".class";
                    Path classFile = tempDir.resolve(classFileName);
                    Path directory = classFile.getParent();
                    Files.createDirectories(directory);
                    Files.write(classFile, generatedFile.openInputStream().readAllBytes());
                }
            }

            // Prepare command to run the class in a separate JVM
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";

            
            List<String> command = new ArrayList<>();
            command.add(javaBin);
            command.add("-cp");
            StringBuilder arg = new StringBuilder();
            for(var cpFile : classPath) {
                arg.append(cpFile.toString() + ":");
            }
            arg.append(tempDir.resolve("CLASS_OUTPUT"));
            command.add(arg.toString());
            command.add(className);

            // Add any command-line arguments
            Collections.addAll(command, args);

            // Start the process
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(new File(tempDir.resolve("CLASS_OUTPUT").toString()));
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            Process process = builder.start();

            // Wait for the process to complete and get exit code
            int exitCode = process.waitFor();
            return exitCode;

        } catch (Exception e) {
            System.err.println("Error running generated class: " + e.getMessage());
            e.printStackTrace();
            return -1;
        } finally {
            // Clean up temporary directory
            try {
                deleteRecursively(tempDir);
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete temp directory: " + e.getMessage());
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var files = Files.list(path)) {
                for (Path file : files.toList()) {
                    deleteRecursively(file);
                }
            }
        }
        Files.delete(path);
    }
}