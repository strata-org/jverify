package org.strata.jverify.verifier.tests.verification.pure;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * The standard invariant of a per-character parser: every element before the
 * loop counter satisfies the property. The quantifier cannot capture the
 * mutating counter directly (javac requires captured locals to be effectively
 * final), so the prefix bound is passed through a parameter of a @Transparent
 * predicate, and the invariant applies that predicate to the counter. The
 * predicate's body is a bare {@code return forall(...)}.
 */
@JVerifyTest(methodsVerified = 3, errorCount = 0)
class PrefixQuantifierInvariant {
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
            if (s[i] < 97 || s[i] > 122) {
                return false;
            }
            i = i + 1;
        }
        return true;
    }
}
