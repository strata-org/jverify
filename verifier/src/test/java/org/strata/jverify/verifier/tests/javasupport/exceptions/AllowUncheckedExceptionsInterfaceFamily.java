package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.AllowUncheckedExceptions;
import org.strata.jverify.testengine.JVerifyTest;

/**
 * An interface method under the project-level {@code --allow-unchecked-exceptions}.
 * It has no body to infer from, so it throws {@code RuntimeException}, whatever its
 * implementations throw, and a call through {@code Op} has to handle that.
 * {@code Div.apply} may throw ArithmeticException, which fits under the interface's
 * type. {@code Fails.apply} may throw an {@code Error}, which does not,
 * so that throw is asserted not to happen.
 */
@JVerifyTest(allowUncheckedExceptions = true,
        continueOnErrors = true,
        exitCode = 4,
        errorCount = 2)
class AllowUncheckedExceptionsInterfaceFamily {
    static class Boom extends Error {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Error'

    interface Op {
        int apply(int b);
    }

    static class Div implements Op {
        @Override
        public int apply(int b) {
            return 10 / b;
        }
    }

    static class Fails implements Op {
        @Override
        public int apply(int b) {
            if (b < 0) {
                throw new Boom();
//              ^ Error: uncaught exception 'org.strata.jverify.verifier.tests.javasupport.exceptions.AllowUncheckedExceptionsInterfaceFamily.Boom' is not thrown does not hold
            }
            return b;
        }
    }

    static int callThrough(Op op, int b) {
        return op.apply(b);
    }

    static int callerCatches(Op op, int b) {
        try {
            return callThrough(op, b);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @AllowUncheckedExceptions(allow = false)
    static int catchesTooLittle(Op op, int b) {
        try {
            return callThrough(op, b);
//                 ^ Error: call does not throw an uncaught 'java.lang.RuntimeException' could not be proved
        } catch (ArithmeticException e) {
            return 0;
        }
    }
}
