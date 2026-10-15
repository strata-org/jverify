package org.strata.java.bytecode.frontend;

import com.sun.source.util.JavacTask;
import org.checkerframework.checker.nullness.qual.Nullable;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The bytecode-reading half of the frontend: compile Java sources with
 * {@code -g -Xjcov} (so the LocalVariableTable and CharacterRangeTable the
 * translation consumes are present), then pool the classfiles per top-level
 * class for the direct bytecode-to-Laurel translation.
 *
 * <p>The CharacterRangeTable's statement spans (see {@link
 * MethodBodyFactory#crtRanges}) become the source ranges downstream Laurel
 * generation stamps on statements for diagnostics. The table is optional: if
 * javac's CRT computation crashes, {@link #compile} retries with plain
 * {@code -g} and the translation falls back to the LineNumberTable.
 */
public final class BytecodeFrontend {

    private BytecodeFrontend() {
    }

    /** One top-level class's classfiles. */
    public record ClassSource(
            String internalName,
            ClassModel classModel,
            /** The SourceFile attribute (e.g. "Foo.java"), or null when absent. */
            String sourceFileName,
            /** The classfiles of this class's nested classes. */
            List<ClassModel> nestedModels) {
    }

    /** The outcome of the javac step. */
    public record CompileResult(
            Path classesDir,
            boolean success,
            List<Diagnostic<? extends JavaFileObject>> diagnostics,
            /**
             * True when {@code -Xjcov} crashed and the classfiles came from the
             * plain-{@code -g} retry, so positions are line- not
             * column-precise through no choice of the caller's.
             */
            boolean degradedToLineNumbers,
            /**
             * What javac knew of the classes the classfiles mention, by fqn ({@link TypeRecorder});
             * empty when the compilation failed.
             */
            Map<String, TypeFacts> types) {

        CompileResult(Path classesDir, boolean success,
                      List<Diagnostic<? extends JavaFileObject>> diagnostics, Map<String, TypeFacts> types) {
            this(classesDir, success, diagnostics, false, types);
        }
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
        return compile(sources, classPath, outputDir, diagnosticOutput, null);
    }

    /**
     * As {@link #compile(List, List, Path, Writer)}, handing each javac task to
     * {@code beforeCall} before it runs: a listener added there, such as
     * constant unfolding's, sees the task's whole compilation.
     */
    public static CompileResult compile(List<? extends JavaFileObject> sources,
                                        List<Path> classPath,
                                        Path outputDir,
                                        Writer diagnosticOutput,
                                        @Nullable Consumer<JavacTask> beforeCall) throws IOException {
        Files.createDirectories(outputDir);
        var full = compileWith(List.of("-g", "-Xjcov", "-proc:none"),
                sources, classPath, outputDir, diagnosticOutput, beforeCall);
        if (!full.internalFailure()) {
            return full.result();
        }
        // javac's CRT computation (-Xjcov) has known crashes -- an AssertionError
        // in CRTable.SourceComputer on an annotated type, e.g. the cast
        // `(@Nat int) x` (SourceComputer has no visitAnnotatedType case, so it
        // hits Assert.error). The CharacterRangeTable is a precision
        // optimisation, not a correctness input: without it the translation
        // falls back to the LineNumberTable (CompilationFacts.Tier.DEBUG),
        // which yields the same diagnostics at line rather than column
        // precision. So retry once with plain `-g`: a crash in an optional
        // attribute must not cost a verdict.
        //
        // The crash aborts genCode part-way, so the output directory can hold
        // classfiles for some classes and not others; it is emptied before the
        // retry so no half-written class survives into the read.
        new PrintWriter(diagnosticOutput, true).println(
                "internal javac error while compiling with -Xjcov (" + full.reason()
                        + "); retrying with -g only, so diagnostics carry line precision");
        deleteContents(outputDir);
        var degraded = compileWith(List.of("-g", "-proc:none"),
                sources, classPath, outputDir, diagnosticOutput, beforeCall);
        var r = degraded.result();
        return new CompileResult(r.classesDir(), r.success(), r.diagnostics(), true, r.types());
    }

    /**
     * One javac invocation: its result, plus the {@code Throwable} that escaped
     * (if any).
     */
    private record Attempt(CompileResult result, Throwable crash) {

        /**
         * True when javac failed for a reason that is not the user's Java code.
         * Two shapes: the throwable escaped {@code call()}, or -- as
         * {@code CRTable}'s AssertionError does -- javac caught it itself,
         * printed "An exception has occurred in the compiler", and returned
         * false. A genuine Java error always produces at least one ERROR
         * diagnostic, so a failure with none is an internal one.
         */
        boolean internalFailure() {
            if (crash != null) {
                return true;
            }
            return !result.success() && result.diagnostics().stream()
                    .noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
        }

        String reason() {
            return crash != null ? String.valueOf(crash)
                    : "javac reported failure with no error diagnostic";
        }
    }

    /** Empty {@code dir} without removing it. */
    private static void deleteContents(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(dir))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private static Attempt compileWith(List<String> options,
                                       List<? extends JavaFileObject> sources,
                                       List<Path> classPath,
                                       Path outputDir,
                                       Writer diagnosticOutput,
                                       @Nullable Consumer<JavacTask> beforeCall) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(outputDir));
            if (!classPath.isEmpty()) {
                fileManager.setLocationFromPaths(StandardLocation.CLASS_PATH, classPath);
            }
            // Without a source path javac compiles the classpath's sources too,
            // into classes that belong to no input file.
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_PATH, List.of());
            var task = (JavacTask) compiler.getTask(new PrintWriter(diagnosticOutput), fileManager,
                    diagnostics, options, null, sources);
            var recorder = new TypeRecorder(task, outputDir);
            task.addTaskListener(recorder);
            if (beforeCall != null) {
                beforeCall.accept(task);
            }
            try {
                boolean success = task.call();
                // A cycle in a failed compilation's record would hide javac's errors.
                return new Attempt(new CompileResult(outputDir, success, diagnostics.getDiagnostics(),
                        success ? recorder.types() : Map.of()), null);
            } catch (RuntimeException | Error e) {
                // The diagnostics collector already holds anything reported
                // before the crash, so a caller that gives up can still show
                // real Java errors rather than only the internal failure.
                return new Attempt(
                        new CompileResult(outputDir, false, diagnostics.getDiagnostics(), Map.of()), e);
            }
        }
    }

    /**
     * Parse every classfile under {@code classesDir} and group them per
     * top-level class (a nested classfile rides with its enclosing class).
     */
    public static List<ClassSource> readClasses(Path classesDir) throws IOException {
        // Parse everything first: classifying one top-level class needs the
        // other classfiles of the same compilation available by internal name.
        Map<String, ClassModel> pool = new LinkedHashMap<>();
        try (Stream<Path> w = Files.walk(classesDir)) {
            w.filter(p -> p.toString().endsWith(".class"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> {
                        try {
                            byte[] bytes = Files.readAllBytes(p);
                            ClassModel cm;
                            try {
                                cm = ClassFile.of().parse(bytes);
                                // parse() is lazy: damage in the method or
                                // attribute region surfaces only when that
                                // region is touched (a ConstantPoolException,
                                // an IllegalArgumentException). Force full
                                // materialization now, inside this guard, so a
                                // bad tail is reported as an unparseable-input
                                // refusal here -- not as a raw crash later in
                                // readClasses or downstream translation.
                                materialize(cm);
                            } catch (IllegalArgumentException e) {
                                // Structurally unreadable (truncated, corrupted,
                                // not a classfile). ConstantPoolException, thrown
                                // for CP damage, is an IllegalArgumentException.
                                throw new UnparseableClassfileException(
                                        "classfile " + p.getFileName() + " is not parseable: "
                                                + messageOf(e), e);
                            }
                            pool.put(cm.thisClass().asInternalName(), cm);
                        } catch (IOException e) {
                            throw new UncheckedIOException("Failed to read classfile " + p, e);
                        }
                    });
        }

        var result = new ArrayList<ClassSource>();
        for (ClassModel cm : pool.values()) {
            if (ClassfileQueries.isNestedClass(cm)) {
                continue; // contributes to its enclosing class's unit
            }
            String internalName = cm.thisClass().asInternalName();
            // Nested classfiles of this top-level class (Outer$Inner...):
            // needed by the Laurel generator to register their methods for
            // verified/skipped counting parity with JFE. The isNestedClass
            // gate keeps a top-level class with '$' in its NAME from also
            // riding along as a nested member.
            List<ClassModel> nested = pool.values().stream()
                    .filter(n -> n != cm && ClassfileQueries.isNestedClass(n)
                            && n.thisClass().asInternalName().startsWith(internalName + "$"))
                    .toList();
            result.add(new ClassSource(
                    internalName,
                    cm,
                    cm.findAttribute(Attributes.sourceFile())
                            .map(a -> a.sourceFile().stringValue())
                            .orElse(null),
                    nested));
        }
        return result;
    }

    /**
     * Force a full parse of a lazily-parsed {@link ClassModel}, so any
     * deferred structural failure (a corrupt constant-pool reference, broken
     * descriptor, damaged code or attribute) throws here -- inside the
     * read-time guard -- rather than on later lazy access downstream. Two
     * layers, both needed (an identity {@code transformClass} bulk-copies
     * lazy regions verbatim and materializes almost nothing):
     * re-emit every method body instruction by instruction, then walk every
     * structure the translation later reads (names, type descriptors, field/
     * method/code/class attributes). Byte-flip fuzzing over a sample class
     * showed this combination catches every mutant that would otherwise crash
     * on those downstream reads, and that every escape is an
     * {@code IllegalArgumentException} (or its {@code ConstantPoolException}
     * subtype), which is what the caller catches.
     */
    private static void materialize(ClassModel cm) {
        ClassFile.of().transformClass(cm, java.lang.classfile.ClassTransform
                .transformingMethodBodies(java.lang.classfile.CodeTransform.ACCEPT_ALL));
        cm.thisClass().asInternalName();
        cm.superclass();
        for (var field : cm.fields()) {
            field.fieldName().stringValue();
            field.fieldTypeSymbol();
            field.attributes().forEach(java.lang.classfile.Attribute::attributeName);
        }
        for (var method : cm.methods()) {
            method.methodName().stringValue();
            method.methodTypeSymbol();
            method.attributes().forEach(java.lang.classfile.Attribute::attributeName);
            method.code().ifPresent(code -> {
                for (var element : code.elementList()) {
                    element.toString();   // forces operand/entry resolution
                }
                ((java.lang.classfile.attribute.CodeAttribute) code).attributes()
                        .forEach(java.lang.classfile.Attribute::attributeName);
            });
        }
        cm.attributes().forEach(java.lang.classfile.Attribute::attributeName);
    }

    /** A throwable's message, or its type when the message is null (never a user-facing "null"). */
    private static String messageOf(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
