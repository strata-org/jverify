package org.strata.jverify.verifier;

import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.strata.jverify.testengine.JVerifyTestEngine;

/**
 * Pins the xfail inversion in {@link JVerifyTestEngine#applyXfailInversion} -- the
 * XPASS tripwire that is the entire point of the scheme -- with a canned check, so
 * both arms are exercised without a full backend run:
 *
 * <ul>
 *   <li>the check still throws an {@link AssertionError} (blocker not cleared) ->
 *       the test is skipped ({@link TestAbortedException} via {@code Assumptions}),
 *       keeping the build green;</li>
 *   <li>the check completes with no failure (blocker cleared) -> a loud XPASS
 *       {@code Assertions.fail} so the stale marker cannot be missed;</li>
 *   <li>the check throws a non-AssertionError (an infra failure) -> it propagates
 *       rather than being swallowed as "still expected to fail".</li>
 * </ul>
 */
class XpassInversionTest {

    @Test
    void stillFailing_isReportedAsSkipped() {
        // A blocker that hasn't cleared surfaces as an AssertionError from the check.
        var aborted = assertThrows(TestAbortedException.class,
                () -> JVerifyTestEngine.applyXfailInversion("[STRATA_GENERICS]",
                        () -> { throw new AssertionError("count mismatch"); }));
        // Skipped-with-reason, not failed: the build stays green and names the blocker.
        assertTrue(aborted.getMessage().contains("Expected failure"),
                "skip reason should mark it an expected failure, was: " + aborted.getMessage());
        assertTrue(aborted.getMessage().contains("STRATA_GENERICS"),
                "skip reason should name the blocker, was: " + aborted.getMessage());
    }

    @Test
    void nowPassing_failsLoudlyAsXpass() {
        // The blocker cleared: the check completes without throwing -> XPASS.
        var failed = assertThrows(AssertionError.class,
                () -> JVerifyTestEngine.applyXfailInversion("[STRATA_GENERICS]",
                        () -> { /* passes */ }));
        assertTrue(failed.getMessage().contains("XPASS"),
                "a now-passing xfail must fail loudly as XPASS, was: " + failed.getMessage());
    }

    @Test
    void infraFailure_propagates_notSwallowed() {
        // A non-AssertionError (infra failure) must NOT be swallowed as expected-failure.
        var thrown = assertThrows(IllegalStateException.class,
                () -> JVerifyTestEngine.applyXfailInversion("[STRATA_GENERICS]",
                        () -> { throw new IllegalStateException("backend did not run"); }));
        assertTrue(thrown.getMessage().contains("backend did not run"));
    }
}
