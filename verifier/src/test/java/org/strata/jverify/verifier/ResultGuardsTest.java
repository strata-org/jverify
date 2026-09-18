package org.strata.jverify.verifier;

import org.junit.jupiter.api.Test;
import org.strata.jverify.common.AnnotatedRange;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.JVerifyTestEngine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the two loud-failure guards in {@link JVerifyTestEngine#checkResults} against
 * canned results: {@link JVerifyResults.DidNotRun} (a backend crash) stays a
 * non-swallowable {@code IllegalStateException} (loud for every test), while a
 * {@link JVerifyResults.NoVerdict} with count expectations raises
 * {@code NoVerdictExpectation} -- an {@code AssertionError} the xfail catch swallows.
 * See {@code NoVerdictExpectation}'s javadoc for why the types differ.
 */
class ResultGuardsTest {

    private static final SourceFile SRC = new SourceFile(
            java.nio.file.Path.of("Probe.java"), "class Probe {}");

    private static JVerifyTest countExpectingAnnotation() {
        // methodsVerified >= 0 sets a count expectation.
        return JVerifyTestEngine.makeJVerifyTestAnnotation(true, 0, /*methodsVerified*/ 1, 0, false, true);
    }

    private static JVerifyTest noCountAnnotation() {
        // all counts default to -1 (no count expectation); exit code 2 (a usage error).
        return JVerifyTestEngine.makeJVerifyTestAnnotation(true, 2, -1, 0, false, true);
    }

    @Test
    void didNotRunFailsLoudEvenForXfail() {
        // A backend crash is infrastructure failure: IllegalStateException, and crucially
        // NOT an AssertionError, so the xfail catch cannot swallow it.
        var r = new JVerifyResults.DidNotRun(List.of(), -1);
        assertThrows(IllegalStateException.class,
                () -> JVerifyTestEngine.checkResults(r, SRC, countExpectingAnnotation(), List.<AnnotatedRange>of()),
                "a DidNotRun result must raise IllegalStateException -- NOT an AssertionError, so "
                        + "the xfail catch cannot swallow a backend crash");
    }

    @Test
    void noVerdictWithCountExpectationRaisesSwallowableAssertion() {
        // NoVerdict + a count expectation is a front-end usage error: NoVerdictExpectation,
        // which IS an AssertionError so an expectedToFail (Strata/front-end gap) test skips
        // green -- while a normal test, having no catch, still fails loud.
        var r = new JVerifyResults.NoVerdict(List.of(), 2);
        assertThrows(JVerifyTestEngine.NoVerdictExpectation.class,
                () -> JVerifyTestEngine.checkResults(r, SRC, countExpectingAnnotation(), List.<AnnotatedRange>of()),
                "a NoVerdict result with a count expectation must raise NoVerdictExpectation "
                        + "(an AssertionError subtype, so the xfail catch swallows it as expected-failure)");
    }

    @Test
    void noVerdictWithoutCountExpectationDoesNotTrip() {
        // A legitimate diagnostic-only usage-error test (exit 2, no counts) must NOT trip
        // the guard -- otherwise the ~25 existing exit-2 tests would break.
        var r = new JVerifyResults.NoVerdict(List.of(), 2);
        assertDoesNotThrow(
                () -> JVerifyTestEngine.checkResults(r, SRC, noCountAnnotation(), List.<AnnotatedRange>of()),
                "a NoVerdict with no count expectation and matching exit code must pass");
    }
}
