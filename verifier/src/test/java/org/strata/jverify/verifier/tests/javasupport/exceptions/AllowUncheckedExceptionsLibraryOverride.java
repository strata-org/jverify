package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * {@code --allow-unchecked-exceptions} does not cover an override of a library
 * method: a call through {@code Object.hashCode} has no {@code throws} in Laurel, so
 * {@code Ratio.hashCode} keeps its per-method check, and the division that may
 * throw fails that method only. The rest of the program is still verified.
 */
@JVerifyTest(allowUncheckedExceptions = true,
        continueOnErrors = true,
        exitCode = 4,
        errorCount = 1)
class AllowUncheckedExceptionsLibraryOverride {
    static class Ratio {
        int denominator;

        @Override
        public int hashCode() {
            return 10 / denominator;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown could not be proved
        }
    }

    static int hashOf(Ratio r) {
        return r.hashCode();
    }

    static int unrelated(int a) {
        return a;
    }
}
