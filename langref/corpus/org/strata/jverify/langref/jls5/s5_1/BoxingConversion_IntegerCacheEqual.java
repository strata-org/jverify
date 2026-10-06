package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.7 -- Boxing Conversion: boxed int values in the range -128..127 must be
// cached (Integer.valueOf), so autoboxed Integer 127 objects are identity-equal.
class BoxingConversion_IntegerCacheEqual {
    static void run() {

        Integer a = 127;
        Integer b = 127;
        check(a == b);
    }
}
