package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.AllowUncheckedExceptions;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.precondition;

/**
 * {@code @AllowUncheckedExceptions} on overriding methods and around constructors.
 *
 * <p>Each method's thrown type comes from its own body, and an override is capped at
 * the type of the method it overrides, as for a checked {@code throws} in Java:
 * {@code Base.m} may throw ArithmeticException, {@code Sub.m} may too, and
 * {@code Quiet.m} throws nothing. {@code CapBase.m} throws nothing, so the division
 * in {@code CapSub.m} is asserted not to happen.
 *
 * <p>Constructors are not covered by the annotation, so an exception a constructor
 * may throw is still asserted not to happen.
 */
@JVerifyTest(continueOnErrors = true,
        exitCode = 4,
        errorCount = 3)
@AllowUncheckedExceptions
class AllowUncheckedExceptionsOverrides {
    static class Base {
        int m(int x) {
            return 100 / x;
        }
    }

    static class Sub extends Base {
        @Override
        int m(int x) {
            return 10 / x;
        }
    }

    static class Quiet extends Base {
        @Override
        int m(int x) {
            return 1;
        }
    }

    static int callerPropagates(Base b, int x) {
        return b.m(x);
    }

    // Catching around a static call: a catch around an instance call that throws
    // is not yet supported by Strata, whether the throws is inferred or declared.
    static int callerCatches(Base b, int x) {
        try {
            return callerPropagates(b, x);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    @AllowUncheckedExceptions(allow = false)
    static int callerUncaught(Base b, int x) {
        return b.m(x);
//      ^ Error: call does not throw an uncaught 'java.lang.ArithmeticException' could not be proved
    }

    static class CapBase {
        int m(int x) {
            return 0;
        }
    }

    static class CapSub extends CapBase {
        @Override
        int m(int x) {
            return 10 / x;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown could not be proved
        }
    }

    // --- Constructors ---

    static class Checked {
        int v;

        Checked(int x) {
            precondition(x > 0);
            v = 10 / x;
        }
    }

    static int makeChecked(int x) {
        precondition(x > 0);
        Checked c = new Checked(x);
        return c.v;
    }

    static class Unchecked {
        int v;

        Unchecked(int x) {
            v = 10 / x;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown could not be proved
        }
    }
}
