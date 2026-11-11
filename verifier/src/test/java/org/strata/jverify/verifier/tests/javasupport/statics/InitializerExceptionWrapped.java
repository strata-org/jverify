package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * An exception that escapes a static initializer reaches the use of the class
 * as an {@code ExceptionInInitializerError} (JLS 12.4.2), so a handler for
 * the exception's own type does not catch it. The Laurel route refuses such
 * an initializer (StaticInitializerThrows); the direct Cortex translation
 * models the wrapping, and this fixture pins it there (cortex-xfails.txt).
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 2, methodsSkipped = 2, errorCount = 0)
class InitializerExceptionWrapped {

    static class Bad {
        static int v = 7;
//      ^ error: Unsupported thrown expression type: IllegalStateException

        static {
            if (v == 7) {
                throw new IllegalStateException();
            }
        }
    }

    static int r;

    static void handlers() {
//              ^ error: the static initializer of 'Bad', which this method may run, could not be translated: Unsupported thrown expression type: IllegalStateException
        try {
            int x = Bad.v;
        } catch (IllegalStateException e) {
            r = 1;
        } catch (Error e) {
            r = 2;
        }
        check(r == 2);
    }
}
