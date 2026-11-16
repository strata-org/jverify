package org.strata.jverify.verifier.tests.interpret;

import org.strata.jverify.InterpretEntry;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * A box of each wrapper class holds the primitive it was made from, at both
 * ends of the primitive's range; a box is unboxed for arithmetic and boxed
 * again; {@code equals} compares the values of two boxes of one class and is
 * false for a box of another; and a box used as an {@code Object}, or passed
 * through a generic method, is cast back to its class and to no other.
 */
@JVerifyTest(
        interpret = true,
        expectedToFail = true,
        blockedBy = {Blocker.STRATA_OTHER},
        exitCode = 0,
        methodsVerified = 4,
        methodsInvalid = 0,
        methodsSkipped = 0,
        errorCount = 0)
class InterpretBoxing {
    static <T> T same(T t) {
        return t;
    }

    static int unbox(Object o) {
        return (Integer) o;
    }

    @InterpretEntry
    static void run() {
        Integer i = 42;
        int back = i;
        check(back == 42);
        check(i.intValue() == 42);
        check(Integer.valueOf(-2147483648).intValue() == -2147483648);

        Long l = 1L << 40;
        check(l == 1099511627776L);
        Short s = (short) -32768;
        check(s == -32768);
        Byte b = (byte) -128;
        check(b == -128 && b < 0);
        Character c = '￿';
        check(c == 65535 && c > 0);
        Boolean yes = true;
        Boolean no = false;
        check(yes && !no);

        Integer sum = i + 1;
        check(sum == 43);
        i++;
        check(i == 43);
        Integer total = 0;
        for (int k = 0; k < 5; k++) {
            total += k;
        }
        check(total == 10);

        check(i.equals(sum));
        check(!i.equals(Integer.valueOf(44)));
        check(!i.equals(l));
        Long big = 43L;
        check(!big.equals(i));
        check(big.equals(43L));
        check(yes.equals(true) && !yes.equals(no));

        Object o = i;
        check(o instanceof Integer);
        check(o instanceof Number);
        check(!(o instanceof Long));
        check(unbox(o) == 43);
        check(same(7) == 7);
        boolean caught = false;
        try {
            Long wrong = (Long) o;
        } catch (ClassCastException e) {
            caught = true;
        }
        check(caught);

        switch (sum) {
            case 43 -> check(true);
            default -> check(false);
        }
    }
}
