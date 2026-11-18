package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * Each of the six wrapper classes is described by its contract: boxing calls
 * {@code valueOf}, unboxing reads the box's value, {@code equals} compares the
 * values of two boxes of one class, and a box used as an {@code Object} is of
 * its own class only. A box never equals a box of another class, also one
 * made in the same expression. A wrapper's {@code equals} leaves a call to
 * {@code Object.equals} on an {@code Object} translated.
 */
@JVerifyTest(methodsVerified = 5, methodsSkipped = 0)
class WrapperContracts {
    static void boxes() {
        Integer i = 1000;
        Long l = 5L;
        Short s = (short) 3;
        Byte b = (byte) 2;
        Character c = 'x';
        Boolean z = true;
        Object o = i;
        check(i + 1 == 1001 && l == 5L && s == 3 && b == 2 && c == 'x' && z);
        check(o instanceof Integer && !(o instanceof Long));
        check(i.equals(1000) && l.equals(5L) && z.equals(true) && !s.equals((short) 4) && b.equals((byte) 2));
        check(((Integer) o).intValue() == 1000);
        check(c.equals('x'));
    }

    static void boxesOfOtherClasses() {
        Long l = 5L;
        Integer i = 5;
        Boolean z = true;
        check(!l.equals(5));
        check(!i.equals((short) 5));
        check(!z.equals(1));
        check(!l.equals(i));
    }

    static boolean boxEquals(Integer i, Object x) {
        return i.equals(x);
    }

    static boolean objectEquals(Object a, Object b) {
        return a.equals(b);
    }
}
