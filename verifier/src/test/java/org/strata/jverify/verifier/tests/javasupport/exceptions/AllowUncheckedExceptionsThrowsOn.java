package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.precondition;
import static org.strata.jverify.JVerify.throwsOn;

/**
 * {@code --allow-unchecked-exceptions} does not cover a method with
 * {@code throwsOn} cases: the cases are stated for the declared type, so an
 * unchecked exception the method may also throw fails that method only (here
 * the division, which also breaks the completeness of its cases). The rest of
 * the program is still verified.
 */
@JVerifyTest(allowUncheckedExceptions = true,
        continueOnErrors = true,
        exitCode = 4,
        methodsVerified = 3,
        methodsInvalid = 1,
        errorCount = 4)
class AllowUncheckedExceptionsThrowsOn {
    static class AppException extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'

    static int checkedAndDivides(int a, int b) throws AppException {
//             ^ Error: assertion does not hold
//             ^ Error: postcondition does not hold
        precondition(b != -1);
        throwsOn(a < 0);
        if (a < 0) {
            throw new AppException();
        }
        return a / b;
//      ^ Error: assertion does not hold
//      ^ Error: divisor is non-zero does not hold
    }

    static int div(int a, int b) {
        precondition(a >= 0);
        return a / b;
    }
}
