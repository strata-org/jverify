package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

/**
 * {@code --allow-unchecked-exceptions} does not cover a method that a subclass
 * inherits as the implementation of an interface method: {@code C} implements
 * {@code I.m} with {@code Base.m}, so a call through {@code I} runs
 * {@code Base.m}. {@code Base.m} keeps its per-method check, so the division
 * that may throw is reported there instead of escaping {@code callThroughI}
 * unnoticed, and {@code C}'s declaration gets a warning that says why.
 */
@JVerifyTest(allowUncheckedExceptions = true,
        continueOnErrors = true,
        exitCode = 4,
        errorCount = 1)
class AllowUncheckedExceptionsInheritedImplementation {
    interface I {
        int m(int b);
    }

    static class Base {
        public int m(int b) {
            return 10 / b;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown could not be proved
        }
    }

    static class C extends Base implements I {
//         ^ warning: 'Base.m' is not covered by @AllowUncheckedExceptions: 'C' inherits it as the implementation of 'I.m'. Override 'm' in 'C', or make 'Base' implement 'I'.
    }

    static int callThroughI(I i, int b) {
        return i.m(b);
    }

    static int callThroughBase(Base base, int b) {
        return base.m(b);
    }
}
