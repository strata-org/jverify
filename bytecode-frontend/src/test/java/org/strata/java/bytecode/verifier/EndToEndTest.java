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
 * Full pipeline: source -> javac -g -Xjcov -> classfiles -> Laurel -> Strata,
 * with exact diagnostic ranges.
 */
class EndToEndTest {

    private static Path libraryClasses() {
        return org.strata.java.bytecode.verifier.VerifierDriver.specLibraryLocation();
    }

    private static VerifierOptions options() {
        return new VerifierOptions(
                new PrintWriter(System.out, true),
                Path.of(System.getProperty("user.dir")),
                List.of(libraryClasses()),
                null, false, true, List.of(), false,
                true, false, false, false, false, null, null, false);
    }

    private JVerifyResults verify(Path dir, String fileName, String source) throws Exception {
        var file = new SourceFile(dir.resolve(fileName), source);
        return new VerifierDriver(options()).verifyJavaFiles(List.of(file));
    }

    @Test
    void assertFalseFails(@TempDir Path tmp) throws Exception {
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
        // `check(false)` on line 8: exact bytecode-native CRT statement span,
        // 8:9-8:22. END is 8:22 -- one past the trailing ';' (col 21), which
        // the statement-level span includes. Pinned exactly so the
        // column-level oracle (which the corpus relaxes) is still exercised
        // somewhere.
        assertEquals(new Range(new Position(8, 9), new Position(8, 22)), withRange.getRange());
        assertEquals(1, ran.verificationResults().verificationFailedMethods());
        // 1 verified: the implicit constructor.
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
        // i + 1).
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
        // countTo + implicit constructor.
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
    void largeLineOffsetRoundTripsThroughStrata(@TempDir Path tmp) throws Exception {
        // A diagnostic deep in a multi-thousand-line file: proves the synthetic
        // (line, column) offset survives the full Raw -> Ion -> Strata -> decode
        // round trip intact, not just the parse boundary. Bytecode line numbers
        // are physical, so blank padding places the failing check at a known
        // large line.
        int checkLine = 3000;
        var sb = new StringBuilder();
        sb.append("import static org.strata.jverify.JVerify.check;\n");   // 1
        sb.append("\n");                                                  // 2
        sb.append("class Deep {\n");                                      // 3
        sb.append("    static void m() {\n");                             // 4
        for (int i = 5; i < checkLine; i++) {
            sb.append("\n");                                              // 5 .. checkLine-1
        }
        sb.append("        check(false);\n");                             // checkLine (col 9)
        sb.append("    }\n");
        sb.append("}\n");
        var results = verify(tmp, "Deep.java", sb.toString());

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
        assertEquals(1, ran.diagnostics().size(), () -> "diagnostics: " + ran.diagnostics());
        var withRange = assertInstanceOf(DiagnosticWithRange.class, ran.diagnostics().getFirst());
        // Exact position at a large line: the offset (checkLine << 10 | 9)
        // decodes back to the same line:column after the whole pipeline.
        assertEquals(new Position(checkLine, 9), withRange.getRange().start(),
                () -> "reported range: " + withRange.getRange());
    }

    @Test
    void loopEndingInASwitchVerifies(@TempDir Path tmp) throws Exception {
        // The back-edge goto must go inside the switch cascade's falling
        // arms: appended after the switch's block, Resolution synthesizes
        // the cascade if and rejects the arms' trailing assignments as
        // incompatible types (bool vs int32).
        var source = """
                import static org.strata.jverify.JVerify.*;

                public class SwitchTail {
                    static int sw(int k) {
                        precondition(k == 1);
                        boolean go = true;
                        int steps = 0;
                        while (go) {
                            invariant(0 <= steps && steps <= 0);
                            switch (k) {
                                case 1: go = false; break;
                                default: steps = steps + 1;
                            }
                        }
                        return steps;
                    }
                }
                """;
        var results = verify(tmp, "SwitchTail.java", source);

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
        assertEquals(List.of(), ran.diagnostics().stream().map(d -> d.getMessage(null)).toList(),
                "no diagnostics expected");
        assertEquals(0, ran.exitCode());
        // sw + implicit constructor.
        assertEquals(2, ran.verificationResults().verificationPassedMethods());
    }

    @Test
    void loopEndingInATryFinallyResolves(@TempDir Path tmp) throws Exception {
        // The back-edge goto must go inside the try body's trailing if/else
        // arms (Laurel's EliminateExceptions unwinds a goto leaving the try
        // through the finally): with the goto after the try, the lowering
        // leaves the if/else in non-tail position and the re-resolution
        // rejects the arm types ("Internal error: resolution after
        // 'EliminateExceptions' introduced this diagnostic: 'if' branches
        // have incompatible types 'bool' and 'int32'").
        //
        // Full verification of a try inside ANY loop is a known Strata
        // limitation independent of the goto placement: loop elimination
        // havocs the $thrown flag EliminateExceptions introduces, so "no
        // exception escapes this `try` uncaught" (and the procedure's
        // implicit no-throw postcondition) cannot be proved -- see
        // StrataLaurel/Tests/EndToEndTests/Execution/StmtExpr/Stmt/Goto.lean,
        // which runs the equivalent Laurel with skipVerification and notes a
        // `while` around the same try fails the same way. Pin exactly those
        // two obligations, and nothing structural, as the outcome.
        var source = """
                import static org.strata.jverify.JVerify.*;

                public class TryTail {
                    static int tf(int k) {
                        precondition(k == 1);
                        boolean go = true;
                        int steps = 0;
                        while (go) {
                            invariant(0 <= steps && steps <= 0 && k == 1);
                            try {
                                if (k == 1) {
                                    go = false;
                                } else {
                                    steps = steps + 1;
                                }
                            } finally {
                                k = 1;
                            }
                        }
                        return steps;
                    }
                }
                """;
        var results = verify(tmp, "TryTail.java", source);

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
        assertEquals(
                List.of("Error: no exception escapes this `try` uncaught could not be proved",
                        "Error: postcondition could not be proved"),
                ran.diagnostics().stream().map(d -> d.getMessage(null)).toList(),
                "only the documented try-in-loop havoc obligations may fail");
        // tf fails those obligations; the implicit constructor verifies.
        assertEquals(1, ran.verificationResults().verificationPassedMethods());
        assertEquals(1, ran.verificationResults().verificationFailedMethods());
    }

    @Test
    void breakInsideATryFinallyInALoopIsRefusedNotVerified(@TempDir Path tmp) throws Exception {
        // javac inlines one finally copy per normal exit of the protected
        // range; only the copy at the range's end is stripped, so the
        // fall-through path would keep its inlined copy AND unwind through
        // the Laurel finally -- the model would run the finally twice per
        // iteration, making c == 6 where Java computes c == 3, and this
        // FALSE check would verify (a missed violation). The shape must be
        // refused, with no verdict for the method.
        var source = """
                import static org.strata.jverify.JVerify.*;

                public class BreakFin {
                    static int m() {
                        int i = 0;
                        int c = 0;
                        while (i < 3) {
                            try {
                                if (i == 10) {
                                    break;
                                }
                                i = i + 1;
                            } finally {
                                c = c + 1;
                            }
                        }
                        check(c == 6);
                        return c;
                    }
                }
                """;
        var results = verify(tmp, "BreakFin.java", source);

        // The refusal stops the run with NO verdict: the false check must
        // neither verify nor merely count as a failure.
        var noVerdict = assertInstanceOf(JVerifyResults.NoVerdict.class, results, () -> "got: " + results);
        assertEquals(1, noVerdict.diagnostics().size(), () -> "diagnostics: " + noVerdict.diagnostics());
        assertTrue(noVerdict.diagnostics().getFirst().getMessage(null).contains("modelled as running twice"),
                () -> noVerdict.diagnostics().getFirst().getMessage(null));
    }

    @Test
    void localFirstStoredInALoopBodyIsVisibleAfterTheLoop(@TempDir Path tmp) throws Exception {
        // t's first store is inside the loop body (not the header), and t is
        // read after the loop: its declaration must be hoisted out of the
        // labelled loop block or `return t` fails to resolve.
        var source = """
                import static org.strata.jverify.JVerify.*;

                public class BodyStore {
                    static int g(int n) {
                        precondition(n >= 0 && n < 100);
                        int i = 0;
                        int t;
                        while (true) {
                            invariant(0 <= i && i <= n + 3);
                            if (i % 2 == 0) {
                                i = i + 1;
                            } else {
                                i = i + 2;
                            }
                            t = i;
                            if (t > n) {
                                break;
                            }
                        }
                        return t;
                    }
                }
                """;
        var results = verify(tmp, "BodyStore.java", source);

        var ran = assertInstanceOf(JVerifyResults.Ran.class, results, () -> "got: " + results);
        assertEquals(List.of(), ran.diagnostics().stream().map(d -> d.getMessage(null)).toList(),
                "no diagnostics expected");
        assertEquals(0, ran.exitCode());
        // g + implicit constructor.
        assertEquals(2, ran.verificationResults().verificationPassedMethods());
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
