package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * A non-static {@code @InterpretEntry} method is flagged: only static methods
 * are translated and runnable as Core entry procedures, so an instance method
 * has no runnable entry point (and no receiver to execute against). The marker
 * is rejected with a clear diagnostic and the method is skipped, while a static
 * no-argument sibling still verifies.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 2,
        methodsSkipped = 1,
	methodsInvalid = 0,
        errorCount = 0
)
class InterpretEntryNonStatic {
    @InterpretEntry
    void runInstance() {
//       ^ error: @InterpretEntry method 'runInstance' must be static
        check(true);
    }

    static void supported() {
        check(true);
    }
}
