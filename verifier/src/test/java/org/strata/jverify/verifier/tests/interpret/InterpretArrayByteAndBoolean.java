package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A {@code boolean[]} and a {@code byte[]} are read and written by the same
 * two instructions, and which of the two an access is on follows from the
 * static type of the array: of a local after a join and in a loop, of a
 * parameter, of a field, of a method result, of a cast and of a conditional
 * expression. The two stay different types at run time. The last check
 * fails, so the run reaches it.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_ARRAYS},
        exitCode = 4,
        methodsVerified = 4,
        methodsInvalid = 1,
        methodsSkipped = 0,
        errorCount = 1)
class InterpretArrayByteAndBoolean {
    boolean[] marks = new boolean[2];
    byte[] codes = new byte[2];

    static byte[] bytes(int n) {
        return new byte[n];
    }

    static int count(boolean[] a) {
        int n = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i]) {
                n++;
            }
        }
        return n;
    }

    static void fill(byte[] a, boolean[] seen, int from) {
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (from + i);
            seen[i] = true;
        }
    }

    @InterpretEntry
    static void run() {
        byte[] small = new byte[2];
        byte[] large = new byte[3];
        boolean pick = small.length == 2;

        byte[] chosen;
        if (pick) {
            chosen = small;
        } else {
            chosen = large;
        }
        chosen[1] = (byte) (small.length + 42);
        check(chosen[1] == 44);
        check(small[1] == 44);
        check(large[1] == 0);

        (pick ? large : small)[0] = -1;
        check((pick ? large : small)[0] == -1);
        check(small[0] == 0);

        boolean[] flags = new boolean[3];
        flags[2] = true;
        check(count(flags) == 1);
        boolean[] other = pick ? flags : new boolean[1];
        other[0] = true;
        check(count(flags) == 2);

        boolean[] seen = new boolean[3];
        fill(large, seen, -129 + small.length);
        check(large[0] == -127 && large[1] == -126 && large[2] == -125);
        check(count(seen) == 3);

        var o = new InterpretArrayByteAndBoolean();
        o.marks[1] = true;
        o.codes[1] = 9;
        check(o.marks[1]);
        check(!o.marks[0]);
        check(o.codes[0] + o.codes[1] == 9);

        bytes(1)[0] = 1;
        byte[] made = bytes(2);
        made[0] = 7;
        check(made[0] == 7);

        Object flagsObject = flags;
        check(flagsObject instanceof boolean[]);
        check(!(flagsObject instanceof byte[]));
        check(!(flagsObject instanceof int[]));
        Object madeObject = made;
        check(madeObject instanceof byte[]);
        check(!(madeObject instanceof boolean[]));
        check(!(madeObject instanceof short[]));
        check(!(madeObject instanceof char[]));
        ((byte[]) madeObject)[1] = 5;
        ((boolean[]) flagsObject)[1] = true;
        check(made[1] == 5);
        check(count(flags) == 3);

        check(made[1] == 6);
//      ^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
