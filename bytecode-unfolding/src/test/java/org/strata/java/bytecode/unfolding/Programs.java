package org.strata.java.bytecode.unfolding;

import com.sun.source.util.JavacTask;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Compile Java sources with javac, plainly or unfolded, and run or inspect the result. */
final class Programs {
    private Programs() {
    }

    /**
     * Compile the {@code .java} files under {@code src} into {@code out}, plainly or unfolded, with the
     * bytecode frontend's {@code -g -Xjcov}.
     */
    static void compile(Path src, Path out, boolean unfold) throws IOException {
        List<Path> files;
        try (var walk = Files.walk(src)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        Files.createDirectories(out);
        var javac = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (StandardJavaFileManager fileManager = javac.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(out));
            var task = (JavacTask) javac.getTask(null, fileManager, diagnostics,
                    List.of("-g", "-Xjcov", "-proc:none", "-nowarn"), null, fileManager.getJavaFileObjectsFromPaths(files));
            if (unfold) {
                ConstantUnfolding.install(task);
            }
            if (!task.call()) {
                throw new AssertionError("javac failed: " + diagnostics.getDiagnostics());
            }
        }
    }

    /**
     * Run {@code className}'s main in a fresh class loader over {@code classes}, as {@code java} runs
     * it (assertions off), and return what it printed. A classfile the JVM's verifier rejects throws
     * here.
     */
    static String run(Path classes, String className) throws Exception {
        PrintStream stdout = System.out;
        var printed = new ByteArrayOutputStream();
        try (var loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            loader.setDefaultAssertionStatus(false);
            System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
            Class.forName(className, true, loader).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(stdout);
        }
        return printed.toString(StandardCharsets.UTF_8);
    }

    /** The opcodes of method {@code name} in {@code classFile}. */
    static List<Opcode> opcodes(Path classFile, String name) throws IOException {
        for (MethodModel m : ClassFile.of().parse(classFile).methods()) {
            if (m.methodName().equalsString(name)) {
                return m.code().orElseThrow().elementStream()
                        .filter(e -> e instanceof Instruction)
                        .map(e -> ((Instruction) e).opcode())
                        .toList();
            }
        }
        throw new AssertionError("no method " + name + " in " + classFile);
    }

    static Path write(Path dir, String relative, String source) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        return file;
    }
}
