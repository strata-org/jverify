package org.strata.java.bytecode.frontend;

import com.github.javaparser.ast.CompilationUnit;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Attributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The bytecode-reading half of the frontend: compile Java sources with
 * {@code -g -Xjcov} (so LocalVariableTable, LineNumberTable, and the
 * CharacterRangeTable are present), then reconstruct a structured JavaParser
 * AST per top-level class via the vendored {@link Decompiler}.
 *
 * <p>Statements in the reconstructed AST carry {@link Decompiler#SPAN}
 * data (line:col ranges from the CharacterRangeTable), which downstream
 * Laurel generation turns into source ranges for diagnostics.
 */
public final class BytecodeFrontend {

    private BytecodeFrontend() {
    }

    /** One reconstructed top-level class. */
    public record DecompiledClass(
            String internalName,
            ClassModel classModel,
            CompilationUnit unit,
            /** The SourceFile attribute (e.g. "Foo.java"), or null when absent. */
            String sourceFileName,
            /** The classfiles of this class's nested classes (folded into {@link #unit}). */
            List<ClassModel> nestedModels) {
    }

    /** The outcome of the javac step. */
    public record CompileResult(
            Path classesDir,
            boolean success,
            List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    }

    /**
     * Compile {@code sources} into {@code outputDir} with {@code -g -Xjcov}.
     * The caller owns {@code outputDir} (typically a temp dir it cleans up).
     *
     * <p>Never throws on a Java compile error: errors are returned as
     * diagnostics so the caller can report them the way JFE reports javac
     * errors from its embedded compiler.
     */
    public static CompileResult compile(List<? extends JavaFileObject> sources,
                                        List<Path> classPath,
                                        Path outputDir,
                                        Writer diagnosticOutput) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        Files.createDirectories(outputDir);
        try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDir));
            if (!classPath.isEmpty()) {
                fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, classPath);
            }
            var options = List.of("-g", "-Xjcov", "-proc:none");
            var task = compiler.getTask(new PrintWriter(diagnosticOutput), fileManager,
                    diagnostics, options, null, sources);
            boolean success;
            try {
                success = task.call();
            } catch (RuntimeException | Error e) {
                // javac's CRT computation (-Xjcov) has known crashes, e.g. an
                // AssertionError in CRTable.SourceComputer on annotated-type
                // casts like `(@Nat int) x`. Surface it as a compile failure
                // instead of taking down the run; the diagnostics collector
                // already holds anything reported before the crash.
                new PrintWriter(diagnosticOutput, true).println(
                        "internal javac error while compiling with -Xjcov: " + e);
                return new CompileResult(outputDir, false, diagnostics.getDiagnostics());
            }
            return new CompileResult(outputDir, success, diagnostics.getDiagnostics());
        }
    }

    /**
     * Parse every classfile under {@code classesDir} and reconstruct one
     * {@link CompilationUnit} per top-level class. Nested classes are folded
     * into their enclosing class's unit (same pooling scheme as
     * {@link Decompiler#sweep}).
     */
    public static List<DecompiledClass> decompile(Path classesDir) throws IOException {
        // Parse everything first: building one top-level class needs the
        // other classfiles of the same compilation available by internal name.
        Map<String, ClassModel> pool = new LinkedHashMap<>();
        try (Stream<Path> w = Files.walk(classesDir)) {
            w.filter(p -> p.toString().endsWith(".class"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> {
                        try {
                            ClassModel cm = ClassFile.of().parse(p);
                            pool.put(cm.thisClass().asInternalName(), cm);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to parse classfile " + p, e);
                        }
                    });
        }

        var result = new ArrayList<DecompiledClass>();
        for (ClassModel cm : pool.values()) {
            if (Decompiler.isNestedClass(cm)) {
                continue; // contributes to its enclosing class's unit
            }
            String internalName = cm.thisClass().asInternalName();
            // Nested classfiles of this top-level class (Outer$Inner...):
            // needed by the Laurel generator to register their methods for
            // verified/skipped counting parity with JFE.
            List<ClassModel> nested = pool.values().stream()
                    .filter(n -> n != cm && n.thisClass().asInternalName().startsWith(internalName + "$"))
                    .toList();
            CompilationUnit unit = Decompiler.build(cm, pool);
            result.add(new DecompiledClass(
                    internalName,
                    cm,
                    unit,
                    cm.findAttribute(Attributes.sourceFile())
                            .map(a -> a.sourceFile().stringValue())
                            .orElse(null),
                    nested));
        }
        return result;
    }
}
