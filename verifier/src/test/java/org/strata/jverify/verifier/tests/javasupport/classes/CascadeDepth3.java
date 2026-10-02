package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * A refusal must cascade to the WHOLE transitive caller chain, not just direct callers.
 *
 * <p>{@code depth3} is refused (a compound assignment is an unsupported expression).
 * {@code depth2} calls it and is demoted. {@code depth1} calls {@code depth2} and must
 * be demoted too: the scan feeds each demotion back and re-runs, so the drop pass never
 * removes a procedure that a surviving caller still references.
 *
 * <p>Before the scan iterated to a fixpoint, {@code depth1} stayed translated while
 * {@code depth2}'s procedure was dropped, and the program died with
 * {@code Resolution failed: '...?static?depth2' is not defined} — an unattributable
 * failure with no per-method verdict, which is exactly what demoting callers exists to
 * prevent. Statics, so each demotion is an error rather than a warning and the whole
 * chain is visible.
 *
 * <p>Each diagnostic is attributed to the METHOD NAME, not to the offending statement,
 * so the carets sit under the declaration. {@code errorCount = 0} because it counts
 * VERIFICATION errors: all three methods are skipped, so none is ever verified.
 * Verified 1 = the implicit constructor; Skipped 3 = the whole chain, which is what
 * pins that the cascade reached depth 2.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 1, methodsSkipped = 3,
             errorCount = 0)
class CascadeDepth3 {

    static int counter;

    static void depth3() {
//              ^ error: static field access is not yet supported
        counter += 1;
    }

    static void depth2() {
//              ^ error: call to 'depth3', which could not be translated
        depth3();
    }

    static void depth1() {
//              ^ error: call to 'depth2', which could not be translated: call to 'depth3', which could not be translated
        depth2();
    }
}
