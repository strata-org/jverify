package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/** A box holds the value it was made from: a check that it holds another fails. */
@JVerifyTest(exitCode = 4, errorCount = 1, methodsVerified = 1, methodsInvalid = 1)
class WrapperContractsError {
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
        check(c.equals('y'));
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion could not be proved
    }
}
