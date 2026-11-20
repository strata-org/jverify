package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;
import static org.strata.jverify.JVerify.throwsOn;

/**
 * Implicit runtime exceptions as real control flow. An integer division by
 * zero throws ArithmeticException and a failing reference cast throws
 * ClassCastException; the bytecode frontend emits each as an explicit
 * {@code throw} ahead of the operation. Uncaught, Laurel asserts the throw is
 * unreachable, so it is an error only when it can happen; caught, it is
 * ordinary control flow, so code that handles the exception verifies.
 *
 * <p>Where the throw cannot be made explicit it stays an assertion on the
 * operation itself ({@code divisor is non-zero}), e.g. in a method whose
 * {@code throws} clause does not cover the exception. In a method whose
 * {@code throws} clause does cover it, the exception propagates to callers like
 * any declared one, and stated {@code throwsOn} cases must cover it.
 *
 * <p>The divisions that produce a value take {@code a >= 0} so the separate
 * int32/int64 overflow obligation ({@code MIN_VALUE / -1}) stays out of the way.
 *
 * <p>The source frontend still models these as plain assertions, so it reports
 * the caught cases as failures and refuses the undeclared throw (XFAIL in
 * source-xfails.txt).
 */
@JVerifyTest(continueOnErrors = true,
        exitCode = 4,
        methodsVerified = 30, // 24 methods below + 6 implicit constructors
        methodsInvalid = 10,
        errorCount = 10)
class ImplicitExceptions {
    static class AppException extends RuntimeException {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.RuntimeException'
    static class ParseError extends AppException {}
    static class CodedException extends AppException { int code; }
    static class Counter {
        int n;
        void set() { n = 1; }
        void record(CodedException x) { n = x.code; }
    }
    static class MyArithmetic extends ArithmeticException {}
//         ^ warning: missing contract for method '<init>' in class 'java.lang.ArithmeticException'

    // --- ArithmeticException ---

    static int divUncaught(int a, int b) {
        precondition(a >= 0);
        return a / b;
//      ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown could not be proved
    }

    static int divGuarded(int a, int b) {
        precondition(a >= 0 && b != 0);
        return a / b;
    }

    static int divCaught(int a, int b) {
        precondition(a >= 0);
        try {
            return a / b;
        } catch (ArithmeticException e) {
            return 0;
        }
    }

    static int remCaughtBySupertype(int a, int b) {
        precondition(a >= 0);
        try {
            return a % b;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    static long longDivCaught(long a, long b) {
        precondition(a >= 0);
        try {
            return a / b;
        } catch (ArithmeticException e) {
            return 0L;
        }
    }

    /** The handler runs exactly when the divisor is zero. */
    static int divCaughtObserved(int a, int b) {
        precondition(a >= 0);
        int r;
        try {
            r = a / b;
        } catch (ArithmeticException e) {
            r = -1;
        }
        check(b != 0 || r == -1);
        return r;
    }

    static int divInConditionCaught(int a, int b) {
        int r;
        try {
            if (a / b > 0) {
                r = 1;
            } else {
                r = 0;
            }
        } catch (ArithmeticException e) {
            r = -1;
        }
        return r;
    }

    /** A handler for a subtype does not catch the ArithmeticException thrown
        here, so it escapes the `try`, which is where it is reported. */
    static int divSubtypeHandler(int a, int b) {
        precondition(a >= 0);
        int r;
        try {
            r = a / b;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown could not be proved
        } catch (MyArithmetic e) {
            r = 0;
        }
        return r;
    }

    /** An inner handler catches the ArithmeticException, so the outer arm,
        unrelated to it, never sees it: the division is still a real throw.
        The outer arm catches the AppException the outer body throws. */
    static int divCaughtInNestedTry(int a, int b) {
        precondition(a >= 0);
        int r;
        try {
            try {
                r = a / b;
            } catch (ArithmeticException e) {
                r = -1;
            }
            if (r < 0) {
                throw new AppException();
            }
        } catch (AppException e) {
            r = -2;
        }
        return r;
    }

    /** A multi-catch arm for an exception the body never throws is harmless. */
    static int divMultiCatch(int a, int b) {
        precondition(a >= 0);
        int r;
        try {
            r = a / b;
        } catch (IllegalStateException | ArithmeticException e) {
            r = 0;
        }
        return r;
    }

    /** An unrelated handler does not catch the ArithmeticException. */
    static int divUnrelatedHandler(int a, int b) {
        precondition(a >= 0);
        int r;
        try {
            r = a / b;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown could not be proved
        } catch (IllegalStateException e) {
            r = 0;
        }
        return r;
    }

    /** The handler sees its arm's type, and so its fields, although the `try`
        also throws an ArithmeticException, which the arm does not catch. */
    static int divHandlerReadsField(int a, int b) {
        precondition(a >= 0);
        int r = 0;
        try {
            r = a / b;
//          ^ Error: uncaught exception 'java.lang.ArithmeticException' is not thrown could not be proved
            CodedException m = new CodedException();
            m.code = 5;
            throw m;
        } catch (CodedException e) {
            r = e.code;
        }
        return r;
    }

    /** A handler whose binding shares its name with a local hoisted to method
        level reads the binding without declaring a second local. */
    static int divCatchNameReused(int a, int b) {
        precondition(a >= 0);
        int r;
        {
            int e;
            if (a > 0) {
                e = 1;
            } else {
                e = 2;
            }
            r = e;
        }
        try {
            r = a / b;
        } catch (ArithmeticException e) {
            r = 0;
        }
        return r;
    }

    /** The handler's call is the method's only heap write: the frame still covers it. */
    static int divHandlerCalls(Counter c, int a, int b) {
        precondition(a >= 0);
        int r;
        try {
            r = a / b;
        } catch (ArithmeticException e) {
            c.set();
            r = 0;
        }
        return r;
    }

    /** The handler passes its binding to a heap-writing call. */
    static int handlerPassesBinding(Counter c, int a) {
        int r = a;
        try {
            if (a == 0) {
                throw new CodedException();
            }
        } catch (CodedException e) {
            c.record(e);
            r = 0;
        }
        return r;
    }

    /** A throws clause that does not cover ArithmeticException keeps the assertion. */
    static boolean divUncoveredThrows(int a, int b) throws java.io.IOException {
        return a / b > 0;
//      ^ Error: divisor is non-zero could not be proved
    }

    /** An unrelated arm further out is never reached: the inner handler catches it. */
    static int divCaughtInsideUnrelatedTry(int a, int b, int x) {
        precondition(a >= 0);
        int r;
        try {
            mayThrowApp(x);
            try {
                r = a / b;
            } catch (ArithmeticException e) {
                r = 0;
            }
        } catch (AppException e) {
            r = -1;
        }
        return r;
    }

    static void mayThrowApp(int x) throws AppException {
        if (x < 0) {
            throw new AppException();
        }
    }

    /** `x = 5` runs only if the division does not throw. */
    static boolean divBeforeAssignment(int a, int b) {
        int x = 1;
        boolean q;
        try {
            q = a / b > (x = 5);
        } catch (ArithmeticException e) {
            check(x == 1);
            q = false;
        }
        return q;
    }

    /** The same with the assignment in a conditional arm. */
    static int divBeforeAssignmentInArm(int a, int b, boolean c) {
        precondition(a >= 0 && a < 1000);
        int x = 1;
        int r;
        try {
            r = a / b + (c ? (x = 5) : 0);
        } catch (ArithmeticException e) {
            check(x == 1);
            r = 0;
        }
        return r;
    }

    // --- A throws clause that covers the exception: it propagates to callers ---

    /** The ArithmeticException leaves through the declared exit, so the method verifies. */
    static int divPropagates(int a, int b) throws RuntimeException {
        precondition(a >= 0);
        return a / b;
    }

    /** ...and the caller that neither catches nor declares it is reported at the call. */
    static int callsDivPropagates(int a) {
        precondition(a >= 0);
        return divPropagates(a, 0);
//      ^ Error: call does not throw an uncaught 'java.lang.RuntimeException' could not be proved
    }

    static int catchesDivPropagates(int a) {
        precondition(a >= 0);
        try {
            return divPropagates(a, 0);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Stated throwsOn cases claim to cover every throwing path; the zero divisor is one. */
    static int divCasesIncomplete(int a, int b) throws RuntimeException {
//             ^ Error: throwsOn cases cover every throwing path could not be proved
        precondition(a >= 0);
        throwsOn(false);
        return a / b;
    }

    static int divCasesComplete(int a, int b) throws RuntimeException {
        precondition(a >= 0);
        throwsOn(b == 0);
        return a / b;
    }

    /** The callee's cases rule out the throw for a non-zero divisor. */
    static int callsDivCasesComplete(int a) {
        precondition(a >= 0);
        return divCasesComplete(a, 1);
    }

    // --- ClassCastException ---

    static ParseError castCaught(AppException e) {
        ParseError p;
        try {
            p = (ParseError) e;
        } catch (ClassCastException c) {
            p = new ParseError();
        }
        return p;
    }

    static ParseError castUncaught(AppException e) {
        return (ParseError) e;
//      ^ Error: uncaught exception 'java.lang.ClassCastException' is not thrown does not hold
    }

    // --- Explicit, undeclared unchecked throws ---

    static int throwUnreachable(int x) {
        precondition(x >= 0);
        if (x < 0) {
            throw new AppException();
        }
        return x;
    }

    static int throwReachable(int x) {
        if (x < 0) {
            throw new AppException();
//          ^ Error: uncaught exception 'org.strata.jverify.verifier.tests.javasupport.exceptions.ImplicitExceptions.AppException' is not thrown could not be proved
        }
        return x;
    }

    static int pureThrowUnreachable(int x) {
        precondition(x >= 0);
        if (x < 0) {
            throw new AppException();
        }
        return x;
    }

    static int pureThrowReachable(int x) {
        if (x < 0) {
            throw new AppException();
//          ^ Error: uncaught exception 'org.strata.jverify.verifier.tests.javasupport.exceptions.ImplicitExceptions.AppException' is not thrown could not be proved
        }
        return x;
    }
}
