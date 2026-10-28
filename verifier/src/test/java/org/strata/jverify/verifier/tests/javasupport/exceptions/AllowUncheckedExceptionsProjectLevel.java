package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.AllowUncheckedExceptions;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.precondition;

/**
 * The project level of {@code @AllowUncheckedExceptions}: the verifier's
 * {@code --allow-unchecked-exceptions} option allows undeclared unchecked
 * exceptions everywhere a nearer annotation does not say otherwise.
 */
@JVerifyTest(allowUncheckedExceptions = true,
        continueOnErrors = true,
        exitCode = 4,
        methodsVerified = 3,
        methodsInvalid = 1,
        errorCount = 1)
class AllowUncheckedExceptionsProjectLevel {
    static int div(int a, int b) {
        precondition(a >= 0);
        return a / b;
    }

    static int callerCatches(int b) {
        try {
            return div(10, b);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    @AllowUncheckedExceptions(allow = false)
    static int denied(int a, int b) {
        precondition(a >= 0);
        return a / b;
//      ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown could not be proved
    }
}
