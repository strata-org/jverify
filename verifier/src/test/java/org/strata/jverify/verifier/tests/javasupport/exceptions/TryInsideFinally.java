package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A try/catch inside a finally block is kept in every copy of the finally body, the
 * normal one and the one the catch-all handler runs: its catch arm still handles
 * what the try inside throws, also when the try/finally is itself inside a try.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 3, methodsInvalid = 2, errorCount = 2)
class TryInsideFinally {
    static int caughtInFinally(int a) {
        precondition(a != 0);
        int r = 0;
        try {
            r = 1;
        } finally {
            try {
                r = r + 10 / a;
            } catch (ArithmeticException e) {
                r = -1;
            }
        }
        check(r == 1 + 10 / a);
        return r;
    }

    static int caughtInFinallyKO(int a) {
        precondition(a != 0);
        int r = 0;
        try {
            r = 1;
        } finally {
            try {
                r = r + 10 / a;
            } catch (ArithmeticException e) {
                r = -1;
            }
        }
        check(r == -1);
//      ^^^^^^^^^^^^^^^ Error: assertion does not hold
        return r;
    }

    static int nestedCaught(int a, int b) {
        int r = 0;
        try {
            try {
                r = 1 / b;
            } finally {
                try {
                    r = 10 / a;
                } catch (ArithmeticException e) {
                    r = -1;
                }
            }
        } catch (ArithmeticException x) {
            r = -2;
        }
        check(b == 0 ? r == -2 : (a == 0 ? r == -1 : r == 10 / a));
        return r;
    }

    static int nestedCaughtKO(int a, int b) {
        int r = 0;
        try {
            try {
                r = 1 / b;
            } finally {
                try {
                    r = 10 / a;
                } catch (ArithmeticException e) {
                    r = -1;
                }
            }
        } catch (ArithmeticException x) {
            r = -2;
        }
        check(r != -1);
//      ^^^^^^^^^^^^^^^ Error: assertion does not hold
        return r;
    }
}
