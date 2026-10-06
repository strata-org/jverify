package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators == and !=: identity comparison of object references
class ReferenceEqualityOperators_5 {
    static void run() {

        // comparison against null
        Box_ReferenceEqualityOperators_5 n = null;
        check(n == null);
    }
}

class Box_ReferenceEqualityOperators_5 {
    int value;
    Box_ReferenceEqualityOperators_5(int value) {
        this.value = value;
    }
}
