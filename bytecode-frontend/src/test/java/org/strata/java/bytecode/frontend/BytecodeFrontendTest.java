package org.strata.java.bytecode.frontend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.verifier.SourceFile;

import java.io.PrintWriter;
import java.lang.classfile.MethodModel;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
