package org.strata.jverify.verifier.tests.algorithms;

import org.strata.jverify.Erased;
import org.strata.jverify.Pure;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_PURE_VALIDATOR, Blocker.FRONTEND_OTHER}, exitCode = 0)
class Base64 {
    static final int PAD = '=';

    @Pure
    @Erased
    static boolean bytes(int[] data) {
        reads(data);
        return forall((int k) -> implies(0 <= k && k < data.length, 0 <= data[k] && data[k] <= 255));
    }

    @Pure
    @Erased
    static boolean sameBelow(int[] a, int[] b, int hi) {
        reads(a);
        reads(b);
        return forall((int k) -> implies(0 <= k && k < hi, a[k] == b[k]));
    }

    @Pure
    static int symbol(int sextet) {
        precondition(0 <= sextet && sextet < 64);
        return sextet < 26 ? 'A' + sextet
             : sextet < 52 ? 'a' + sextet - 26
             : sextet < 62 ? '0' + sextet - 52
             : sextet == 62 ? '+' : '/';
    }

    @Pure
    static int sextet(int c) {
        return 'A' <= c && c <= 'Z' ? c - 'A'
             : 'a' <= c && c <= 'z' ? c - 'a' + 26
             : '0' <= c && c <= '9' ? c - '0' + 52
             : c == '+' ? 62 : c == '/' ? 63 : -1;
    }

    @Pure
    @Erased
    static int padOf(int[] text) {
        reads(text);
        precondition(text.length % 4 == 0);
        return text.length == 0 ? 0 : (text[text.length - 1] == PAD ? 1 : 0) + (text[text.length - 2] == PAD ? 1 : 0);
    }

    @Pure
    @Erased
    static boolean symbolsBelow(int[] a, int hi) {
        reads(a);
        return forall((int k) -> implies(0 <= k && k < hi, sextet(a[k]) >= 0));
    }

    @Pure
    @Erased
    static boolean wellFormed(int[] text) {
        reads(text);
        return forall((int k) -> implies(0 <= k && k < text.length, sextet(text[k]) >= 0 || (text[k] == PAD && k >= text.length - 2)));
    }

    @Pure
    static int encodedLength(int n) {
        precondition(0 <= n && n <= 100000);
        return ((n + 2) / 3) * 4;
    }

    static int[] encode(int[] data) {
        precondition(bytes(data));
        precondition(data.length <= 100000);
        postcondition((int[] res) -> res.length == encodedLength(data.length));
        postcondition((int[] res) -> wellFormed(res));
        postcondition((int[] res) -> padOf(res) == (3 - data.length % 3) % 3);

        int[] out = new int[encodedLength(data.length)];
        int i = 0;
        int o = 0;
        while (i + 2 < data.length) {
            modifies(out);
            invariant(0 <= i && i <= data.length && i % 3 == 0 && o == i / 3 * 4);
            invariant(symbolsBelow(out, o));
            int triple = (data[i] << 16) | (data[i + 1] << 8) | data[i + 2];
            out[o] = symbol((triple >> 18) & 63);
            out[o + 1] = symbol((triple >> 12) & 63);
            out[o + 2] = symbol((triple >> 6) & 63);
            out[o + 3] = symbol(triple & 63);
            i = i + 3;
            o = o + 4;
        }
        int rest = data.length - i;
        if (rest == 1) {
            int triple = data[i] << 16;
            out[o] = symbol((triple >> 18) & 63);
            out[o + 1] = symbol((triple >> 12) & 63);
            out[o + 2] = PAD;
            out[o + 3] = PAD;
        } else if (rest == 2) {
            int triple = (data[i] << 16) | (data[i + 1] << 8);
            out[o] = symbol((triple >> 18) & 63);
            out[o + 1] = symbol((triple >> 12) & 63);
            out[o + 2] = symbol((triple >> 6) & 63);
            out[o + 3] = PAD;
        }
        return out;
    }

    static int[] decode(int[] text) {
        precondition(text.length % 4 == 0 && text.length <= 133336);
        postcondition((int[] res) -> !wellFormed(text) || res.length == text.length / 4 * 3 - padOf(text));
        int pad = text.length == 0 ? 0 : (text[text.length - 1] == PAD ? 1 : 0) + (text[text.length - 2] == PAD ? 1 : 0);
        int[] out = new int[text.length / 4 * 3 - pad];
        int i = 0;
        int o = 0;
        while (i < text.length) {
            modifies(out);
            invariant(0 <= i && i <= text.length && i % 4 == 0 && o == i / 4 * 3);
            int s0 = sextet(text[i]);
            int s1 = sextet(text[i + 1]);
            int s2 = text[i + 2] == PAD ? 0 : sextet(text[i + 2]);
            int s3 = text[i + 3] == PAD ? 0 : sextet(text[i + 3]);
            if (s0 < 0 || s1 < 0 || s2 < 0 || s3 < 0) {
                return new int[0];
            }
            int triple = (s0 << 18) | (s1 << 12) | (s2 << 6) | s3;
            if (o < out.length) {
                out[o] = (triple >> 16) & 255;
            }
            if (o + 1 < out.length) {
                out[o + 1] = (triple >> 8) & 255;
            }
            if (o + 2 < out.length) {
                out[o + 2] = triple & 255;
            }
            i = i + 4;
            o = o + 3;
        }
        return out;
    }

    static void roundTrip(int[] data) {
        precondition(bytes(data));
        precondition(data.length <= 100000);
        int[] back = decode(encode(data));
        check(back.length == data.length);
        check(sameBelow(back, data, data.length));
    }
}
