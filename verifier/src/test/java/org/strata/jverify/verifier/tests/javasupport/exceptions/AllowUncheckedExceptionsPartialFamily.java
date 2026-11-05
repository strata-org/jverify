package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.AllowUncheckedExceptions;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * An overriding family that {@code @AllowUncheckedExceptions} covers only in part.
 * An annotated override is capped at the type of the method it overrides, so when
 * only {@code Sub.m} is annotated its division is still asserted not to happen.
 * When only the base is annotated, {@code Base2.m} gets its inferred type and the
 * override, which throws nothing, needs none.
 */
@JVerifyTest(continueOnErrors = true,
        exitCode = 4,
        errorCount = 1)
class AllowUncheckedExceptionsPartialFamily {
    // Only the override is annotated.
    static class Base {
        int m(int x) {
            return 0;
        }
    }

    static class Sub extends Base {
        @Override
        @AllowUncheckedExceptions
        int m(int x) {
            return 10 / x;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown does not hold
        }
    }

    // Only the base is annotated.
    @AllowUncheckedExceptions
    static class Base2 {
        int m(int x) {
            return 10 / x;
        }
    }

    static class Sub2 extends Base2 {
        @Override
        int m(int x) {
            return 1;
        }
    }

    static int unrelated(int a) {
        return a;
    }
}
