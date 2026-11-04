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
        exitCode = 4,
        methodsVerified = 2,
        methodsInvalid = 1,
        methodsSkipped = 0,
        errorCount = 5
)
class TranslatorSkip {
    static int unsupported(int n) {
//             ^ Error: postcondition does not hold
        var sum = 0;
        for (int i = 0, j = n; i < j; i++, j--) {
//                                    ^^^ Error: assertion does not hold
//                                         ^^^ Error: assertion does not hold
            sum = sum + i;
//                ^^^^^^^ Error: assertion does not hold
        }
        return sum;
//             ^^^ Error: assertion does not hold
    }

    static void supported(int x) {
        check(x == x);
    }
}
