package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators: Integer values just outside the
// cached range (>= 128) yield distinct references, so == is false.
class ReferenceEquality_IntegerCache128 {
    static void run() {

        Integer a = 128;
        Integer b = 128;
        check(a != b);
    }
}
