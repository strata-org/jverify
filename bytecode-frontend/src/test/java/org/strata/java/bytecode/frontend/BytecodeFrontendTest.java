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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-with--g--Xjcov then classify: the pooled classfiles must carry the
 * direct translation's ingredients — a structurable CFG, the JVerify spec
 * calls (which survive as invokestatic), the CLASS-retention annotations from
 * our library fork, and CharacterRangeTable spans on statements.
 */
class BytecodeFrontendTest {

    private static final String SOURCE = """
            import org.strata.jverify.Pure;
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

                @Pure
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
        // The CLASS-retention spec library (bytecode-frontend-lib.jar), passed by the
        // test task. The JVerify class LOADED here is JFE's SOURCE-retention
        // :library, whose annotations would not survive into classfiles.
        return java.nio.file.Path.of(java.util.Objects.requireNonNull(
                System.getProperty("bytecode.library.jar"),
                "bytecode.library.jar system property not set"));
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

        // CLASS-retention @Pure from our library fork is visible in bytecode.
        boolean pure = sumTo.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleAnnotations())
                .map(a -> a.annotations().stream().anyMatch(x ->
                        java.lang.constant.ClassDesc.ofDescriptor(x.className().stringValue())
                                .displayName().equals("Pure")))
                .orElse(false);
        assertTrue(pure, "sumTo should carry @Pure");

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
}
