package org.strata.jverify.verifier.tests.javasupport.exceptions.uncheckedpkg;

import org.strata.jverify.AllowUncheckedExceptions;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.precondition;

/**
 * The package level of {@code @AllowUncheckedExceptions}: this package's
 * {@code package-info.java} allows undeclared unchecked exceptions, which a class
 * annotation can still deny.
 */
@JVerifyTest(continueOnErrors = true,
        additionalFiles = { "./package-info.java" },
        exitCode = 4,
        methodsVerified = 4,
        methodsInvalid = 1,
        errorCount = 1)
public class PackageLevel {
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
    static class Denied {
        static int div(int a, int b) {
            precondition(a >= 0);
            return a / b;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown does not hold
        }
    }
}
