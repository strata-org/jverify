package org.strata.jverify.verifier.tests.javasupport.lambdas;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A contract lambda whose body is a {@code try} statement is refused, and the method
 * with it is skipped; the other method and the implicit constructor still verify.
 */
@JVerifyTest(continueOnErrors = true, exitCode = 0, methodsVerified = 2, methodsSkipped = 1, errorCount = 0)
class ContractLambdaWithTry {
    static int tenOver(int x) {
//                            ^ error: Unsupported lambda body
        postcondition((int r) -> {
            try {
                return r == 10 / x;
            } catch (ArithmeticException e) {
                return true;
            }
        });
        return x == 0 ? 0 : 10 / x;
    }

    static int plain(int x) {
        postcondition((int r) -> r == x);
        return x;
    }
}
