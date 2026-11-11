package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * On the Laurel route a static initializer that may let an exception escape
 * is refused, like a constructor: neither is allowed an undeclared unchecked
 * exception. The initializer is reported where it starts, and each method
 * whose use of the class runs it is refused with that reason. (The Cortex
 * route models the exception instead: InitializerExceptionWrapped.)
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 2, methodsSkipped = 2, errorCount = 0)
class StaticInitializerThrows {

    static class Bad {
        static int value;

        static {
//      ^ error: Unsupported thrown expression type: IllegalStateException
            if (value == 0) {
                throw new IllegalStateException();
            }
        }
    }

    static int use() {
//             ^ error: the static initializer of 'Bad', which this method may run, could not be translated: Unsupported thrown expression type: IllegalStateException
        return Bad.value;
    }
}
