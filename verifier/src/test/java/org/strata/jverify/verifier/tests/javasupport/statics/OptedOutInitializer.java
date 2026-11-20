package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * The static initializer of a class opted out of verification is not
 * translated, so a method whose use of the class may run it is refused, as a
 * call to an opted-out method is: verified against a call that does nothing,
 * the method would keep {@code observed == 1} across the initializer that
 * assigns 7.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsSkipped = 3, errorCount = 0)
class OptedOutInitializer {

    static int observed;

    @Verify(false)
    static class Holder {
        static int value = 5;

        static {
            observed = 7;
        }
    }

    static void m() {
//              ^ error: the static initializer of 'Holder', which this method may run, is not verified
        observed = 1;
        int v = Holder.value;
        check(observed == 1);
    }
}
