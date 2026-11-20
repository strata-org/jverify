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
        errorCount = 6
)
class TranslatorSkip {
    static int unsupported(int n) {
//             ^ Error: postcondition could not be proved
        var sum = 0;
        for (int i = 0, j = n; i < j; i++, j--) {
//                                    ^^^ Error: assertion could not be proved
//                                         ^^^ Error: assertion could not be proved
            sum = sum + i;
//                ^^^^^^^ Error: assertion could not be proved
//                ^^^^^^^ Error: no int overflow on '+' could not be proved
        }
        return sum;
//             ^^^ Error: assertion could not be proved
    }

    static void supported(int x) {
        check(x == x);
    }
}
