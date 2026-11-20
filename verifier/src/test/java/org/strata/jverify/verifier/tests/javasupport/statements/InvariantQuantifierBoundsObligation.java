package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * The definedness obligation of a quantifier inside a loop invariant is NOT
 * dropped. Strata strips the injected precondition asserts from the
 * invariant expression itself (a proof block is not lowerable in a spec
 * position); the obligation survives because LoopInvariantWellFormedness
 * plants a statement-position copy of every invariant before the loop, and
 * the asserts are checked there. This fixture pins that: the quantifier's
 * bound is off by one ({@code j <= i}, and {@code i} can reach
 * {@code s.length}), so the array read inside the quantifier must fail its
 * bounds precondition. (The invariant is also not inductive with that bound,
 * so the proof of the invariant itself fails too.)
 */
@JVerifyTest(exitCode = 4, methodsInvalid = 1, errorCount = 3)
class InvariantQuantifierBoundsObligation {
    static boolean offByOne(byte[] s) {
        int i = 0;
        while (i < s.length) {
            final int i0 = i;
            invariant(0 <= i && i <= s.length);
            invariant(forall((int j) -> implies(0 <= j && j <= i0, s[j] >= 97)));
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: precondition does not hold
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            if (s[i] < 97) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }
}
