package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.precondition;

/**
 * A @Transparent method whose body contains a loop is translated as written, so
 * that Strata, not the front end, decides what a transparent body may contain.
 * Strata does not yet accept a loop there and reports it.
 */
@JVerifyTest(exitCode = 4)
class PureMethodWithLoop {

    @Transparent
    static boolean withForLoop(int n) {
        precondition(n >= 0);
        boolean acc = false;
        for (int i = 0; i < n; i = i + 1) {
//      ^^^ Error: loops are not YET supported in transparent bodies or contracts
            acc = !acc;
        }
        return acc;
    }

    @Transparent
    static boolean withWhileLoop(int n) {
        precondition(n >= 0);
        boolean acc = false;
        int i = 0;
        while (i < n) {
//      ^^^^^ Error: loops are not YET supported in transparent bodies or contracts
            acc = !acc;
            i = i + 1;
        }
        return acc;
    }

    @Transparent
    static boolean withDoLoop(int n) {
        precondition(n >= 0);
        boolean acc = false;
        int i = 0;
        do {
            acc = !acc;
            i = i + 1;
        } while (i < n);
//      ^^^^^^^^^^^^^^^^ Error: loops are not YET supported in transparent bodies or contracts
        return acc;
    }
}
