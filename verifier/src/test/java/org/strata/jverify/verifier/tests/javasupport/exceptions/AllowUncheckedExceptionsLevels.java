package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.AllowUncheckedExceptions;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.precondition;

/**
 * {@code @AllowUncheckedExceptions} at the method and class levels. Where it is
 * allowed, an unchecked exception that may leave a method undeclared makes it a
 * thrown exception of that method (its {@code throws} is inferred), so the method
 * verifies and its callers have to handle the exception. Where it is not (the
 * default), the escape is asserted not to happen, as in ImplicitExceptions.
 *
 * <p>The innermost annotation wins, unless an enclosing one says
 * {@code override = true}. The package and project levels are
 * {@code uncheckedpkg.PackageLevel} and {@code AllowUncheckedExceptionsProjectLevel}.
 */
@JVerifyTest(continueOnErrors = true,
        exitCode = 4,
        methodsVerified = 11,
        methodsInvalid = 4,
        errorCount = 4)
class AllowUncheckedExceptionsLevels {
    static class AppChecked extends Exception {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'

    // --- Method level ---

    @AllowUncheckedExceptions
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

    static int callerUncaught(int b) {
        return div(10, b);
//      ^ Error: call does not throw an uncaught 'java.lang.ArithmeticException' does not hold
    }

    @AllowUncheckedExceptions
    static int callerPropagates(int b) {
        return div(10, b);
    }

    static int callerOfPropagating(int b) {
        try {
            return callerPropagates(b);
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    static int notAllowed(int a, int b) {
        precondition(a >= 0);
        return a / b;
//      ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown does not hold
    }

    // A declared checked exception is joined with the inferred unchecked one:
    // the method throws their common supertype, java.lang.Exception, so a
    // caller has to catch Exception, not just the two arms Java needs.
    @AllowUncheckedExceptions
    static int declaredAndInferred(int a, int b) throws AppChecked {
        precondition(a >= 0);
        if (b < 0) {
            throw new AppChecked();
        }
        return a / b;
    }

    static int callerOfDeclared(int b) {
        try {
            return declaredAndInferred(10, b);
        } catch (AppChecked e) {
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    // --- Class level ---

    @AllowUncheckedExceptions
    static class Allowed {
        static int div(int a, int b) {
            precondition(a >= 0);
            return a / b;
        }

        @AllowUncheckedExceptions(allow = false)
        static int denied(int a, int b) {
            precondition(a >= 0);
            return a / b;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown does not hold
        }
    }

    @AllowUncheckedExceptions(allow = false, override = true)
    static class DeniedOverride {
        @AllowUncheckedExceptions
        static int div(int a, int b) {
            precondition(a >= 0);
            return a / b;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown does not hold
        }
    }
}
