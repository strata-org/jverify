package org.strata.jverify.langref.jls5.s5_1;

import static org.strata.jverify.JVerify.*;

// JLS 5.1.7 -- Boxing Conversion: values outside the Integer cache range are not
// required to be identity-equal, and in practice HotSpot creates distinct objects.
class BoxingConversion_IntegerCacheDistinct {
    static void run() {

        Integer a = 128;
        Integer b = 128;
        check(a != b && a.equals(b));
    }
}
