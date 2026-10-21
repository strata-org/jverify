package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER}, exitCode = 0)
class Leb128 {
    static final int MAX_BYTES = 5;

    static int encode(int value, int[] out) {
        precondition(value >= 0);
        precondition(out.length >= MAX_BYTES);
        modifies(out);
        postcondition((int len) -> 1 <= len && len <= MAX_BYTES);

        int v = value;
        int n = 0;
        while (v >= 128) {
            modifies(out);
            invariant(0 <= n && n < MAX_BYTES && v >= 0);
            invariant(n < 1 || v <= 16777215);
            invariant(n < 2 || v <= 131071);
            invariant(n < 3 || v <= 1023);
            invariant(n < 4 || v <= 7);
            out[n] = (v & 127) | 128;
            v = v >>> 7;
            n = n + 1;
        }
        out[n] = v;
        return n + 1;
    }

    static int decode(int[] in, int len) {
        precondition(0 <= len && len <= in.length);
        postcondition((int res) -> res >= -1);

        long result = 0;
        int shift = 0;
        int i = 0;
        while (i < len) {
            invariant(0 <= i && i <= len && shift == 7 * i && i < MAX_BYTES);
            invariant(0 <= result && result < (1L << shift));
            int b = in[i];
            if (b < 0 || b > 255) {
                return -1;
            }
            result = result | ((long) (b & 127) << shift);
            if ((b & 128) == 0) {
                return result <= 2147483647L ? (int) result : -1;
            }
            shift = shift + 7;
            i = i + 1;
            if (i == MAX_BYTES) {
                return -1;
            }
        }
        return -1;
    }

    static void roundTrip(int value) {
        precondition(value >= 0);
        int[] buf = new int[MAX_BYTES];
        int len = encode(value, buf);
        check(decode(buf, len) == value);
    }
}
