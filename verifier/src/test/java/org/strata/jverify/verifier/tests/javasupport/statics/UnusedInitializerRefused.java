package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A static initializer that cannot be translated is reported where it starts,
 * even when no translated method uses its class: its checks would otherwise
 * go unverified while the run reports success.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsSkipped = 1, errorCount = 0)
class UnusedInitializerRefused {

    static class Bad {
        static int value;

        static {
//      ^ error: Unsupported thrown expression type: IllegalStateException
            if (value == 0) {
                throw new IllegalStateException();
            }
            check(false);
        }
    }

    static int ok() {
        return 1;
    }
}
