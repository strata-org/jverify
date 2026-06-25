package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;

/**
 * A @Pure method whose body contains a loop cannot be a transparent Laurel
 * function body (Strata function bodies allow only let-bindings and a final
 * if-then-else), so it is emitted uninterpreted instead of an untranslatable
 * block expression.
 *
 * <p>The test running at all is the regression guard: previously such a method
 * crashed translation with "block expression should have been lowered". The
 * checks confirm the resulting function is congruent (same input, same output)
 * but its interpreted loop result is not provable. (The helpers return boolean
 * to avoid the unrelated "constrained return types on functions" limitation.)
 */
@JVerifyTest(exitCode = 4, methodsVerified = 5, errorCount = 1)
class PureMethodWithLoop {

    @Pure
    static boolean withForLoop(int n) {
        precondition(n >= 0);
        boolean acc = false;
        for (int i = 0; i < n; i = i + 1) {
            acc = !acc;
        }
        return acc;
    }

    @Pure
    static boolean withWhileLoop(int n) {
        precondition(n >= 0);
        boolean acc = false;
        int i = 0;
        while (i < n) {
            acc = !acc;
            i = i + 1;
        }
        return acc;
    }

    @Pure
    static boolean withDoLoop(int n) {
        precondition(n >= 0);
        boolean acc = false;
        int i = 0;
        do {
            acc = !acc;
            i = i + 1;
        } while (i < n);
        return acc;
    }

    // Uninterpreted functions are congruent: equal inputs give equal outputs.
    static void congruenceHolds() {
        check(withForLoop(3) == withForLoop(3));
        check(withWhileLoop(3) == withWhileLoop(3));
        check(withDoLoop(3) == withDoLoop(3));
    }

    // The interpreted loop result is NOT provable: the body was dropped and
    // the function emitted uninterpreted, so its value is unknown.
    static void interpretedResultNotProvable(int n) {
        precondition(n >= 0);
        check(withForLoop(n));
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
