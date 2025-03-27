package com.aws.jverify.plugin;

import com.google.errorprone.annotations.Var;
import com.google.testing.compile.Compilation;
import com.sun.source.doctree.ValueTree;
import org.jetbrains.annotations.NotNull;

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
     */
    public static int runGeneratedClassAndGetExitCode(
            Compilation compilation, Iterable<File> classPath, String qualifiedClassName, String... args) {

        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("generated-classes");
        } catch (IOException e) {
            System.err.println("Failed to create temp directory: " + e.getMessage());
            return -1;
        }

        try {
            writeCompiledClassFiles(compilation, tempDir);

            // Prepare command to run the class in a separate JVM
            var javaBin = ProcessHandle.current().info().command();
            if (javaBin.isEmpty()) {
                System.err.println("Failed to find Java executable");
                return -1;
            }

            var process = getProcess(classPath, qualifiedClassName, args, javaBin.get(), tempDir);

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

    @NotNull
    private static Process getProcess(Iterable<File> classPath, String qualifiedClassName, String[] args, String javaBin, Path tempDir) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        StringBuilder arg = new StringBuilder();
        for(var cpFile : classPath) {
            arg.append(cpFile.toString()).append(File.pathSeparator);
        }
        arg.append(tempDir.resolve("CLASS_OUTPUT"));
        command.add(arg.toString());
        command.add(qualifiedClassName);

        Collections.addAll(command, args);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(new File(tempDir.resolve("CLASS_OUTPUT").toString()));
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        return builder.start();
    }

    private static void writeCompiledClassFiles(Compilation compilation, Path tempDir) throws IOException {
        for (JavaFileObject generatedFile : compilation.generatedFiles()) {
            if (generatedFile.getKind() != JavaFileObject.Kind.CLASS) {
                continue;
            }
            var binaryName = generatedFile.getName()
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

    public static void deleteRecursively(Path path) throws IOException {
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