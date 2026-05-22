package org.strata.jverify.verifier.tests.verification;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * Regression test for #398. Asserts that a translator-rejected method
 * produces a diagnostic, flips its status to Skipped, and leaves siblings
 * verifiable. The trigger is the multi-init for refusal — if that form
 * gains support, swap the trigger; the test covers the catch wrapper, not
 * the construct.
 */
@JVerifyTest(
        continueOnErrors = true,
        exitCode = 0,
        methodsVerified = 2,
        methodsSkipped = 1,
        errorCount = 0
)
class TranslatorSkip {
    static int unsupported(int n) {
//             ^ error: Multi-init or multi-step for loops are not supported
        var sum = 0;
        for (int i = 0, j = n; i < j; i++, j--) {
            sum = sum + i;
        }
        return sum;
    }

    static void supported(int x) {
        check(x == x);
    }
}
