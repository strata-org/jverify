package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.AllowUncheckedExceptions;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * A generic override overrides its base through a bridge method
 * ({@code apply(Number)} overrides {@code apply(Object)}), so it is in the same
 * overriding family. When {@code @AllowUncheckedExceptions} covers only the
 * override, the override is capped at the base's type, which throws nothing, as
 * for a non-generic family: its division is still asserted not to happen, and the
 * rest of the program is still verified.
 */
@JVerifyTest(continueOnErrors = true,
        exitCode = 4,
        methodsVerified = 3,
        methodsInvalid = 1,
        errorCount = 1)
class AllowUncheckedExceptionsGenericFamily {
    interface Op<T> {
        int apply(T x, int d);
    }

    static class Impl<T extends Number> implements Op<T> {
        @Override
        @AllowUncheckedExceptions
        public int apply(T x, int d) {
            return 10 / d;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown does not hold
        }
    }

    static int unrelated(int a) {
        return a;
    }
}
