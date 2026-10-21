package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR}, exitCode = 0)
class LeftPad {
    @Pure
    @Erased
    static boolean paddedBelow(int[] res, int c, int hi) {
        reads(res);
        return forall((int k) -> implies(0 <= k && k < hi, res[k] == c));
    }

    @Pure
    @Erased
    static boolean copiedBelow(int[] res, int[] s, int pad, int hi) {
        reads(res);
        reads(s);
        return forall((int k) -> implies(0 <= k && k < hi, res[pad + k] == s[k]));
    }

    static int[] leftPad(int[] s, int c, int n) {
        precondition(0 <= n && n <= 100000);
        precondition(s.length <= 100000);
        postcondition((int[] res) -> res.length == (n > s.length ? n : s.length)
                && paddedBelow(res, c, res.length - s.length)
                && copiedBelow(res, s, res.length - s.length, s.length));

        int len = n > s.length ? n : s.length;
        int pad = len - s.length;
        int[] res = new int[len];

        int i = 0;
        while (i < pad) {
            modifies(res);
            invariant(0 <= i && i <= pad);
            invariant(res.length == len);
            invariant(paddedBelow(res, c, i));

            res[i] = c;
            i = i + 1;
        }

        int j = 0;
        while (j < s.length) {
            modifies(res);
            invariant(0 <= j && j <= s.length);
            invariant(res.length == len);
            invariant(paddedBelow(res, c, pad));
            invariant(copiedBelow(res, s, pad, j));

            res[pad + j] = s[j];
            j = j + 1;
        }
        return res;
    }
}
