package org.strata.java.bytecode.frontend;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.verifier.SourceFile;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-with--g--Xjcov then classify: the pooled classfiles must carry the
 * direct translation's ingredients — a structurable CFG, the JVerify spec
 * calls (which survive as invokestatic), the CLASS-retention spec
 * annotations, and CharacterRangeTable spans on statements.
 */
class BytecodeFrontendTest {

    private static final String SOURCE = """
            import org.strata.jverify.Transparent;
            import static org.strata.jverify.JVerify.*;

            public class Sample {
                public static int clamp(int lo, int hi, int x) {
                    precondition(lo <= hi);
                    int result;
                    if (x < lo) {
                        result = lo;
                    } else {
                        if (x > hi) {
                            result = hi;
                        } else {
                            result = x;
                        }
                    }
                    check(result >= lo);
                    return result;
                }

                @Transparent
                public static int sumTo(int n) {
                    precondition(n >= 0);
                    int total = 0;
                    int i = 0;
                    while (i < n) {
                        i = i + 1;
                        total = total + i;
                    }
                    return total;
                }
            }
            """;

    private static Path jverifyLibraryClasses() {
        return org.strata.java.bytecode.verifier.VerifierDriver.specLibraryLocation();
    }

    @Test
    void compileAndReadClasses(@TempDir Path tmp) throws Exception {
        var compileResult = BytecodeFrontend.compile(
                List.of(new SourceFile(tmp.resolve("Sample.java"), SOURCE)),
                List.of(jverifyLibraryClasses()),
                tmp.resolve("classes"),
                new PrintWriter(System.err));
        assertTrue(compileResult.success(),
                () -> "javac failed: " + compileResult.diagnostics());

        var classes = BytecodeFrontend.readClasses(compileResult.classesDir());
        assertEquals(1, classes.size(), "one top-level class");
        var sample = classes.getFirst();
        assertEquals("Sample", sample.internalName());
        assertEquals("Sample.java", sample.sourceFileName());

        var cm = sample.classModel();
        MethodModel clamp = methodOf(cm, "clamp");
        MethodModel sumTo = methodOf(cm, "sumTo");

        // The per-method translation ingredients are recoverable: a CFG with
        // real control flow, dominators (so if/while structure), and hoisting.
        var clampShape = MethodBodyFactory.bodyOf(cm, clamp);
        assertNotNull(clampShape, "clamp has Code");
        assertNotNull(clampShape.dom(), "clamp has real control flow (an if)");
        var sumToShape = MethodBodyFactory.bodyOf(cm, sumTo);
        assertNotNull(sumToShape.dom(), "sumTo has real control flow (a while)");
        assertTrue(sumToShape.dom().loops.keySet().stream().findAny().isPresent(),
                "sumTo contains a loop header");

        // Spec calls survive as invokestatic against the JVerify facade.
        var clampCalls = clampShape.seq().stream()
                .filter(e -> e instanceof java.lang.classfile.instruction.InvokeInstruction)
                .map(e -> ((java.lang.classfile.instruction.InvokeInstruction) e).name().stringValue())
                .toList();
        assertTrue(clampCalls.contains("precondition"), () -> "calls: " + clampCalls);
        assertTrue(clampCalls.contains("check"), () -> "calls: " + clampCalls);

        // CLASS-retention @Transparent from the spec library is visible in bytecode.
        boolean pure = sumTo.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleAnnotations())
                .map(a -> a.annotations().stream().anyMatch(x ->
                        java.lang.constant.ClassDesc.ofDescriptor(x.className().stringValue())
                                .displayName().equals("Transparent")))
                .orElse(false);
        assertTrue(pure, "sumTo should carry @Transparent");

        // CharacterRangeTable spans are present with real line numbers.
        assertFalse(clampShape.crt().isEmpty(), "statements should carry CRT spans");
        var firstSpan = clampShape.crt().getFirst().span();
        assertTrue(firstSpan.beginLine() > 0, () -> "span should have a real line: " + firstSpan);
    }

    private static MethodModel methodOf(java.lang.classfile.ClassModel cm, String name) {
        return cm.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    void compileErrorIsReportedNotThrown(@TempDir Path tmp) throws Exception {
        var result = BytecodeFrontend.compile(
                List.of(new SourceFile(tmp.resolve("Bad.java"), "public class Bad { int x = ; }")),
                List.of(),
                tmp.resolve("classes"),
                new PrintWriter(System.err));
        assertFalse(result.success());
        assertFalse(result.diagnostics().isEmpty(), "javac diagnostics should be captured");
    }

    /** A classfile directory the recorder cannot read fails the compile's caller, not javac. */
    @Test
    void aRecordingFailureIsThrownAfterTheCompilation(@TempDir Path tmp) {
        var task = (JavacTask) ToolProvider.getSystemJavaCompiler().getTask(null, null, null, null, null, null);
        var recorder = new TypeRecorder(task, tmp.resolve("missing"));
        recorder.finished(new TaskEvent(TaskEvent.Kind.COMPILATION));
        assertThrows(NoSuchFileException.class, recorder::types);
    }

    /** {@code dep.A} and {@code dep.B} extend each other, which javac accepts of classfiles a program only mentions. */
    @Test
    void aSuperclassCycleOnTheClasspathFailsTheCompile(@TempDir Path tmp) throws Exception {
        Path dep = Files.createDirectories(tmp.resolve("classpath/dep"));
        ClassDesc a = ClassDesc.of("dep.A");
        ClassDesc b = ClassDesc.of("dep.B");
        Files.write(dep.resolve("A.class"), ClassFile.of().build(a, c -> c.withFlags(ClassFile.ACC_PUBLIC).withSuperclass(b)));
        Files.write(dep.resolve("B.class"), ClassFile.of().build(b, c -> c.withFlags(ClassFile.ACC_PUBLIC).withSuperclass(a)));
        var e = assertThrows(IOException.class, () -> BytecodeFrontend.compile(
                List.of(new SourceFile(tmp.resolve("U.java"), "class U { static void m(dep.A a) { } }")),
                List.of(tmp.resolve("classpath")), tmp.resolve("classes"), new PrintWriter(System.err)));
        assertTrue(e.getMessage().startsWith("cyclic inheritance involving dep."), e::getMessage);
    }

    /**
     * An SQL statement's 9000 positional parameters read, from {@code VALUES}' {@code L} to the {@code ;},
     * like a class name with 9000 {@code $}s, too many even for a memoized search over its splits.
     */
    @Test
    void aStringConstantWithManyDollarSignsCompilesPromptly(@TempDir Path tmp) {
        String params = IntStream.rangeClosed(1, 9000).mapToObj(i -> "$" + i).collect(Collectors.joining(", "));
        var source = new SourceFile(tmp.resolve("Sql.java"),
                "class Sql { static final String INSERT = \"INSERT INTO t VALUES (" + params + ");\"; }");
        var result = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> BytecodeFrontend.compile(
                List.of(source), List.of(), tmp.resolve("classes"), new PrintWriter(System.err)));
        assertTrue(result.success(), () -> String.valueOf(result.diagnostics()));
    }

    /**
     * Eight string constants of about 60000 {@code L}s, any of which could start a class name. Rescanning one
     * from each {@code L} takes about the timeout, hence eight, and javac pools equal constants, hence unequal.
     */
    @Test
    void longRunsOfLsCompilePromptly(@TempDir Path tmp) {
        String constants = IntStream.range(0, 8)
                .mapToObj(i -> "static final String S" + i + " = \"" + "L".repeat(60000 - i) + "\";")
                .collect(Collectors.joining(" "));
        var source = new SourceFile(tmp.resolve("Ls.java"), "class Ls { " + constants + " }");
        var result = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> BytecodeFrontend.compile(
                List.of(source), List.of(), tmp.resolve("classes"), new PrintWriter(System.err)));
        assertTrue(result.success(), () -> String.valueOf(result.diagnostics()));
    }

    /**
     * {@code U} mentions {@code dep.Box} and {@code dep.Lists.Node.Link}, a member of a member class, only in its
     * field's signature; InnerClasses also lists its anonymous class, which has no outer class.
     */
    @Test
    void classesOnlyASignatureMentionsAreRecorded(@TempDir Path tmp) throws Exception {
        var dep = BytecodeFrontend.compile(List.of(
                        new SourceFile(tmp.resolve("Box.java"), "package dep; public class Box<T> { }"),
                        new SourceFile(tmp.resolve("Lists.java"), "package dep; public class Lists {"
                                + " public static class Node { public static class Link { } } }")),
                List.of(), tmp.resolve("classpath"), new PrintWriter(System.err));
        assertTrue(dep.success(), () -> String.valueOf(dep.diagnostics()));
        var result = BytecodeFrontend.compile(List.of(new SourceFile(tmp.resolve("U.java"),
                        "class U { java.util.List<dep.Box<dep.Lists.Node.Link>> f; Object o = new Object() { }; }")),
                List.of(tmp.resolve("classpath")), tmp.resolve("classes"), new PrintWriter(System.err));
        assertTrue(result.success(), () -> String.valueOf(result.diagnostics()));
        assertTrue(result.types().keySet().containsAll(List.of("dep.Box", "dep.Lists.Node.Link")),
                () -> String.valueOf(result.types().keySet()));
    }
}
