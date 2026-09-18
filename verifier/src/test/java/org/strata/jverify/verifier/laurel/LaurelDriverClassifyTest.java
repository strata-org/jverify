package org.strata.jverify.verifier.laurel;

import org.junit.jupiter.api.Test;
import org.strata.jverify.common.Position;
import org.strata.jverify.common.Range;
import org.strata.jverify.verifier.JVerifyResults;
import org.strata.jverify.verifier.VerificationResults;

import javax.tools.Diagnostic;
import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link LaurelDriver#classifyRun}: the exit-code classification that the
 * sealed {@link JVerifyResults} tripwire depends on. The load-bearing rule is
 * "non-zero backend exit => DidNotRun, even when diagnostics were parsed" -- a
 * crash mid-print must not become a partial Ran verdict that an xfail test
 * swallows as a false green. A refactor that re-gates the abort path on
 * diagnostics.isEmpty() fails here.
 */
class LaurelDriverClassifyTest {

    private static final URI ABORT_URI = Paths.get("strata").toUri();
    private static final VerificationResults VERDICT = new VerificationResults(1, 0, 0, 0);

    private static List<Diagnostic<?>> oneDiagnostic() {
        var pos = new Position(3, 1);
        var diags = new ArrayList<Diagnostic<?>>();
        diags.add(new LaurelDriver.StrataDiagnostic(ABORT_URI, new Range(pos, pos), "assertion does not hold"));
        return diags;
    }

    @Test
    void nonZeroExitWithDiagnosticsIsDidNotRun() {
        // The newly-closed hole: a crash mid-print must not return Ran.
        var r = LaurelDriver.classifyRun(1, oneDiagnostic(), "boom", ABORT_URI, VERDICT);
        assertInstanceOf(JVerifyResults.DidNotRun.class, r);
        assertEquals(1, r.exitCode());
    }

    @Test
    void nonZeroExitWithoutDiagnosticsIsDidNotRunWithAbortDiagnostic() {
        var r = LaurelDriver.classifyRun(1, new ArrayList<>(), "boom", ABORT_URI, VERDICT);
        assertInstanceOf(JVerifyResults.DidNotRun.class, r);
        assertEquals(1, r.diagnostics().size());
        assertTrue(r.diagnostics().getFirst().getMessage(null).contains("verification aborted"),
                "the #447 abort backstop diagnostic must be synthesized for the empty case");
    }

    @Test
    void zeroExitWithDiagnosticsIsRanWithExit4() {
        var r = LaurelDriver.classifyRun(0, oneDiagnostic(), "", ABORT_URI, VERDICT);
        assertInstanceOf(JVerifyResults.Ran.class, r);
        assertEquals(4, r.exitCode());
    }

    @Test
    void zeroExitWithoutDiagnosticsIsRanWithExit0() {
        var r = LaurelDriver.classifyRun(0, new ArrayList<>(), "", ABORT_URI, VERDICT);
        assertInstanceOf(JVerifyResults.Ran.class, r);
        assertEquals(0, r.exitCode());
    }
}
