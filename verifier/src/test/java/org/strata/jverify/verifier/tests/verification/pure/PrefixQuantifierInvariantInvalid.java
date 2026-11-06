package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Negative control for {@link PrefixQuantifierInvariant}: the loop body also
 * accepts 'A'..'`' (65..96), so the prefix invariant must fail to prove.
 */
@JVerifyTest(exitCode = 4, methodsInvalid = 1, errorCount = 1)
class PrefixQuantifierInvariantInvalid {
    @Transparent
    @Erased
    static boolean lowerBelow(byte[] s, int hi) {
        precondition(0 <= hi && hi <= s.length);
        reads(s);
        return forall((int j) -> implies(0 <= j && j < hi, s[j] >= 97 && s[j] <= 122));
    }

    static boolean allLower(byte[] s) {
        postcondition((boolean r) -> implies(r, lowerBelow(s, s.length)));
        int i = 0;
        while (i < s.length) {
            invariant(0 <= i && i <= s.length);
            invariant(lowerBelow(s, i));
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
            if (s[i] < 65 || s[i] > 122) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }
}
