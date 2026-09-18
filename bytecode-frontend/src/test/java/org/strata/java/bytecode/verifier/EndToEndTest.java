package org.strata.java.bytecode.verifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.DiagnosticWithRange;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.SourceFile;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full pipeline: source -> javac -g -Xjcov -> classfiles -> Laurel -> Strata.
 * The fixtures mirror JFE's AssertFalse / ShouldVerify-class tests, including
 * the expected diagnostic ranges (JFE convention: the whole spec-call
 * expression, semicolon excluded).
 */
class EndToEndTest {

    private static Path libraryClasses() {
        // The CLASS-retention spec library (bytecode-frontend-lib.jar), passed by the
        // test task. The JVerify class LOADED here is JFE's SOURCE-retention
        // :library, whose annotations would not survive into classfiles.
        return Path.of(java.util.Objects.requireNonNull(
                System.getProperty("bytecode.library.jar"),
                "bytecode.library.jar system property not set"));
    }

    private static VerifierOptions options() {
        return new VerifierOptions(
                new PrintWriter(System.out, true),
                Path.of(System.getProperty("user.dir")),
                List.of(libraryClasses()),
                null, false, true, List.of(), false,
                true, false, false, false, false, null, null);
    }

    private JVerifyResults verify(Path dir, String fileName, String source) throws Exception {
        var file = new SourceFile(dir.resolve(fileName), source);
        return new VerifierDriver(options()).verifyJavaFiles(List.of(file));
    }

    @Test
    void assertFalseFails(@TempDir Path tmp) throws Exception {
        // Mirror of JFE's verifier/src/test/resources/AssertFalse.java.
        var source = """
                package org.strata.jverify.verifier.tests;

                import static org.strata.jverify.JVerify.check;

                class AssertFalse {

                    static void Foo() {
                        check(false);
                    }
                }
                """;
        var results = verify(tmp, "AssertFalse.java", source);

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
        assertEquals(4, ran.exitCode());
        assertEquals(1, ran.diagnostics().size(), () -> "diagnostics: " + ran.diagnostics());
        var diagnostic = ran.diagnostics().getFirst();
        assertTrue(diagnostic.getMessage(null).contains("assertion does not hold"),
                () -> diagnostic.getMessage(null));
        var withRange = assertInstanceOf(DiagnosticWithRange.class, diagnostic);
        // `check(false)` on line 8, cols 9..21 (semicolon excluded).
        assertEquals(new Range(new Position(8, 9), new Position(8, 21)), withRange.getRange());
        assertEquals(1, ran.verificationResults().verificationFailedMethods());
        // 1 verified: the implicit constructor (JFE counts it the same way).
        assertEquals(1, ran.verificationResults().verificationPassedMethods());
    }

    @Test
    void contractsAndControlFlowVerify(@TempDir Path tmp) throws Exception {
        var source = """
                import static org.strata.jverify.JVerify.*;

                public class Clamp {
                    static int clamp(int lo, int hi, int x) {
                        precondition(lo <= hi);
                        postcondition(true);
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
                        check(result <= hi);
                        return result;
                    }
                }
                """;
        var results = verify(tmp, "Clamp.java", source);

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
        assertEquals(List.of(), ran.diagnostics(), "no diagnostics expected");
        assertEquals(0, ran.exitCode());
        // clamp + implicit constructor.
        assertEquals(2, ran.verificationResults().verificationPassedMethods());
    }

    @Test
    void loopWithInvariantVerifies(@TempDir Path tmp) throws Exception {
        // Both invariants are needed (i >= 0 for the int32 constraint on
        // i + 1): verified against JFE, which fails without the first one too.
        var source = """
                import static org.strata.jverify.JVerify.*;

                public class Count {
                    static int countTo(int n) {
                        precondition(n >= 0);
                        int i = 0;
                        while (i < n) {
                            invariant(i >= 0);
                            invariant(i <= n);
                            i = i + 1;
                        }
                        check(i == n);
                        return i;
                    }
                }
                """;
        var results = verify(tmp, "Count.java", source);

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
        assertEquals(List.of(), ran.diagnostics(), "no diagnostics expected");
        assertEquals(0, ran.exitCode());
        // countTo + implicit constructor (JFE reports 2 as well).
        assertEquals(2, ran.verificationResults().verificationPassedMethods());
    }

    @Test
    void preconditionViolationAtCallSite(@TempDir Path tmp) throws Exception {
        var source = """
                import static org.strata.jverify.JVerify.*;

                public class Calls {
                    static int half(int x) {
                        precondition(x >= 0);
                        return x / 2;
                    }

                    static int bad() {
                        return half(-4);
                    }
                }
                """;
        var results = verify(tmp, "Calls.java", source);

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
        assertEquals(4, ran.exitCode());
        assertEquals(1, ran.diagnostics().size(), () -> "diagnostics: " + ran.diagnostics());
        assertTrue(ran.diagnostics().getFirst().getMessage(null).toLowerCase().contains("precondition"),
                () -> ran.diagnostics().getFirst().getMessage(null));
        // half + implicit constructor verify; bad fails.
        assertEquals(2, ran.verificationResults().verificationPassedMethods());
        assertEquals(1, ran.verificationResults().verificationFailedMethods());
    }

    @Test
    void javaErrorStopsWithNoVerdict(@TempDir Path tmp) throws Exception {
        var results = verify(tmp, "Bad.java", "public class Bad { int x = ; }");
        var noVerdict = assertInstanceOf(JVerifyResults.NoVerdict.class, results, () -> "got: " + results);
        assertTrue(noVerdict.diagnostics().stream()
                        .anyMatch(d -> d.getMessage(null).contains("Java compilation had an error")),
                () -> "diagnostics: " + noVerdict.diagnostics());
    }
}
