package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A static field of each supported array type. The initializer builds the
 * arrays, and an element read or written through the field has the element
 * type's range: the byte, short and char elements cover both ends of theirs.
 * A field assigned a new array holds the element type's zero values.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 0,
        methodsVerified = 3,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretArrayStaticElementTypes {
    static boolean[] flags = {false, true};
    static byte[] bytes = {-128, 127, 0};
    static char[] chars = {'a', '\uffff'};
    static short[] shorts = {-32768, 32767};
    static int[] ints = {Integer.MIN_VALUE, Integer.MAX_VALUE};
    static long[] longs = {Long.MIN_VALUE, 1L << 40};

    @InterpretEntry
    static void run() {
        check(flags.length == 2 && !flags[0] && flags[1]);
        flags[0] = true;
        flags[1] = false;
        check(flags[0] && !flags[1]);

        check(bytes.length == 3 && bytes[0] == -128 && bytes[1] == 127 && bytes[2] == 0);
        bytes[0] = 127;
        bytes[1] = -128;
        bytes[2] = (byte) -56;
        check(bytes[0] == 127 && bytes[1] == -128 && bytes[2] == -56 && bytes[2] < 0);

        check(chars.length == 2 && chars[0] == 'a' && chars[1] == 65535 && chars[1] > 0);
        chars[0]++;
        chars[1] = 0;
        check(chars[0] == 'b' && chars[1] == 0);
        chars[1] = '\uffff';
        check(chars[1] == 65535 && chars[1] > 0);

        check(shorts.length == 2 && shorts[0] == -32768 && shorts[1] == 32767);
        shorts[0] = 32767;
        shorts[1] = -32768;
        check(shorts[0] == 32767 && shorts[1] == -32768 && shorts[1] < 0);

        check(ints.length == 2 && ints[0] == Integer.MIN_VALUE && ints[1] == Integer.MAX_VALUE);
        ints[0]++;
        ints[1] -= 1;
        check(ints[0] == -2147483647 && ints[1] == 2147483646);

        check(longs.length == 2 && longs[0] == Long.MIN_VALUE && longs[1] == 1099511627776L);
        longs[0] = Long.MAX_VALUE;
        longs[1]++;
        check(longs[0] == Long.MAX_VALUE && longs[1] == 1099511627777L);

        flags = new boolean[1];
        bytes = new byte[1];
        chars = new char[1];
        shorts = new short[1];
        ints = new int[1];
        longs = new long[1];
        check(!flags[0] && bytes[0] == 0 && chars[0] == 0 && shorts[0] == 0 && ints[0] == 0 && longs[0] == 0L);
    }
}
