package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators: autoboxing Integer values in the
// cached range [-128, 127] returns the same reference, so == holds.
class ReferenceEquality_IntegerCache127 {
    static void run() {

        Integer a = 127;
        Integer b = 127;
        check(a == b);
    }
}
