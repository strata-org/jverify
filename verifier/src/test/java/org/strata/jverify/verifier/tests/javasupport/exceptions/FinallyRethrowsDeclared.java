package org.strata.jverify.verifier.tests.javasupport.exceptions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * javac's finally handler rethrows the exception it saved in a local. The rethrow keeps
 * the type the try body throws, so it fits the method's declared {@code throws}.
 */
@JVerifyTest(exitCode = 4, methodsVerified = 3, methodsInvalid = 1, errorCount = 1)
class FinallyRethrowsDeclared {
    static class Failure extends Exception {
        Failure() {
//               ^ warning: missing contract for method '<init>' in class 'java.lang.Exception'
        }
    }

    static int g(int x) throws Failure {
        if (x == 0) {
            throw new Failure();
        }
        return x;
    }

    static int f(int x) throws Failure {
        int r = 0;
        try {
            r = g(x);
        } finally {
            r = r + 0;
        }
        return r;
    }

    static int fKO(int x) throws Failure {
        int r = 0;
        try {
            r = g(x);
        } finally {
            r = r + 0;
        }
        check(r == 0);
//      ^^^^^^^^^^^^^^ Error: assertion could not be proved
        return r;
    }
}
