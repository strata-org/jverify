package org.strata.java.bytecode.frontend;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.WhileStmt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.verifier.SourceFile;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-with--g--Xjcov then decompile: the reconstructed JavaParser AST
 * must contain the structured control flow, the JVerify spec calls (which
 * survive as invokestatic), the CLASS-retention annotations from our
 * library fork, and CharacterRangeTable spans on statements.
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
    void compileAndDecompile(@TempDir Path tmp) throws Exception {
        var compileResult = BytecodeFrontend.compile(
                List.of(new SourceFile(tmp.resolve("Sample.java"), SOURCE)),
                List.of(jverifyLibraryClasses()),
                tmp.resolve("classes"),
                new PrintWriter(System.err));
        assertTrue(compileResult.success(),
                () -> "javac failed: " + compileResult.diagnostics());

        var classes = BytecodeFrontend.decompile(compileResult.classesDir());
        assertEquals(1, classes.size(), "one top-level class");
        var sample = classes.getFirst();
        assertEquals("Sample", sample.internalName());
        assertEquals("Sample.java", sample.sourceFileName());

        var unit = sample.unit();
        var methods = unit.findAll(MethodDeclaration.class);
        assertEquals(2, methods.size(), () -> "methods in:\n" + unit);

        var clamp = methods.stream().filter(m -> m.getNameAsString().equals("clamp")).findFirst().orElseThrow();
        var sumTo = methods.stream().filter(m -> m.getNameAsString().equals("sumTo")).findFirst().orElseThrow();

        // Control flow reconstructed as structured statements.
        assertFalse(clamp.findAll(IfStmt.class).isEmpty(), () -> "clamp should contain an if:\n" + clamp);
        assertFalse(sumTo.findAll(WhileStmt.class).isEmpty(), () -> "sumTo should contain a while:\n" + sumTo);

        // Spec calls survive as (static) method calls.
        var clampCalls = clamp.findAll(MethodCallExpr.class).stream()
                .map(MethodCallExpr::getNameAsString).toList();
        assertTrue(clampCalls.contains("precondition"), () -> "calls: " + clampCalls + "\n" + clamp);
        assertTrue(clampCalls.contains("check"), () -> "calls: " + clampCalls + "\n" + clamp);

        // CLASS-retention @Pure from our library fork is visible in bytecode.
        assertTrue(sumTo.getAnnotationByName("Pure").isPresent(),
                () -> "sumTo should carry @Pure:\n" + sumTo);

        // CharacterRangeTable spans are attached to reconstructed statements.
        var spannedStatements = clamp.findAll(Statement.class).stream()
                .filter(s -> s.containsData(Decompiler.SPAN))
                .toList();
        assertFalse(spannedStatements.isEmpty(), "statements should carry CRT spans");
        var firstSpan = spannedStatements.getFirst().getData(Decompiler.SPAN);
        assertNotNull(firstSpan);
        assertTrue(firstSpan.beginLine() > 0, () -> "span should have a real line: " + firstSpan);
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
