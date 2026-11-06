package org.strata.jverify.verifier.tests.javasupport.statements;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Negative control for {@link LoopCounterSnapshot}: the loop body also
 * accepts 'A'..'`' (65..96), so the snapshotted prefix invariant must fail
 * to prove.
 */
@JVerifyTest(exitCode = 4, methodsInvalid = 1, errorCount = 1)
class LoopCounterSnapshotInvalid {
    static boolean allLower(byte[] s) {
        int i = 0;
        while (i < s.length) {
            final int i0 = i;
            invariant(0 <= i && i <= s.length);
            invariant(forall((int j) -> implies(0 <= j && j < i0, s[j] >= 97 && s[j] <= 122)));
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            if (s[i] < 65 || s[i] > 122) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }
}
