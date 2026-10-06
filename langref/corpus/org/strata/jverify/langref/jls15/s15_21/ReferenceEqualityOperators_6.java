package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators == and !=: identity comparison of object references
class ReferenceEqualityOperators_6 {
    static void run() {

        Box_ReferenceEqualityOperators_6 a = new Box_ReferenceEqualityOperators_6(1);
        check(a != null);
    }
}

class Box_ReferenceEqualityOperators_6 {
    int value;
    Box_ReferenceEqualityOperators_6(int value) {
        this.value = value;
    }
}
