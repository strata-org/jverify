package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, exitCode = 4)
class Adler32Invalid {
    static final int MOD = 65521;

    @Transparent
    @Erased
    static boolean bytes(int[] data) {
        reads(data);
        return forall((int k) -> implies(0 <= k && k < data.length, 0 <= data[k] && data[k] <= 255));
    }

    @Transparent
    @Erased
    static int sumA(int[] data, @Unbounded int n) {
        reads(data);
        decreases(n);
        if (n <= 0) {
            return 1;
        }
        return (sumA(data, n - 1) + data[n - 1]) % MOD;
    }

    @Transparent
    @Erased
    static int sumB(int[] data, @Unbounded int n) {
        reads(data);
        decreases(n);
        if (n <= 0) {
            return 0;
        }
        return (sumB(data, n - 1) + sumA(data, n)) % MOD;
    }

    static int checksum(int[] data) {
        precondition(bytes(data));
        postcondition((int res) -> res == ((sumB(data, data.length) << 16) | sumA(data, data.length)));

        int a = 1;
        int b = 0;
        int i = 0;
        while (i < data.length) {
            invariant(0 <= i && i <= data.length);
            invariant(a == sumA(data, i) && 0 <= a && a < MOD);
            invariant(b == sumB(data, i) && 0 <= b && b < MOD);
//                    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved

            a = (a + data[i]) % MOD;
            b = (b + data[i]) % MOD;
            i = i + 1;
        }
        return (b << 16) | a;
    }
}
