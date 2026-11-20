package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Arrays of each supported element type hold what was stored, start at the
 * element type's zero value and report their length. The byte, short and char
 * elements cover both ends of their ranges, so a store that sign-extended a
 * char or zero-extended a byte or short would fail a check.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 0,
        methodsVerified = 2,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretArrayElements {
    @InterpretEntry
    static void run() {
        boolean[] flags = new boolean[2];
        check(flags.length == 2 && !flags[0] && !flags[1]);
        flags[1] = true;
        check(!flags[0] && flags[1]);

        byte[] bytes = new byte[3];
        check(bytes.length == 3 && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 0);
        bytes[0] = -128;
        bytes[1] = 127;
        bytes[2] = (byte) -56;
        check(bytes[0] == -128 && bytes[1] == 127 && bytes[2] == -56);
        check(bytes[0] < 0 && bytes[2] < 0);

        char[] chars = new char[2];
        check(chars.length == 2 && chars[0] == 0 && chars[1] == 0);
        chars[0] = 'a';
        chars[1] = '\uffff';
        check(chars[0] == 'a' && chars[1] == 65535 && chars[1] > 0);
        chars[0]++;
        check(chars[0] == 'b');

        short[] shorts = new short[2];
        check(shorts.length == 2 && shorts[0] == 0 && shorts[1] == 0);
        shorts[0] = -32768;
        shorts[1] = 32767;
        check(shorts[0] == -32768 && shorts[1] == 32767 && shorts[0] < 0);

        int[] ints = {1, 2, 3};
        check(ints.length == 3 && ints[0] + ints[1] + ints[2] == 6);
        ints[1] = Integer.MIN_VALUE;
        check(ints[0] == 1 && ints[1] == Integer.MIN_VALUE && ints[2] == 3);

        long[] longs = new long[2];
        check(longs.length == 2 && longs[0] == 0L && longs[1] == 0L);
        longs[0] = Long.MIN_VALUE;
        longs[1] = 1L << 40;
        check(longs[0] == Long.MIN_VALUE && longs[1] == 1099511627776L);

        int[] empty = new int[0];
        check(empty.length == 0);
    }
}
