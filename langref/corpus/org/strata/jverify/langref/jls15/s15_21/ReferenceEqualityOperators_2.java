package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators == and !=: identity comparison of object references
class ReferenceEqualityOperators_2 {
    static void run() {

        Box_ReferenceEqualityOperators_2 a = new Box_ReferenceEqualityOperators_2(1);
        Box_ReferenceEqualityOperators_2 b = new Box_ReferenceEqualityOperators_2(1);
        check(!(a == b));
    }
}

class Box_ReferenceEqualityOperators_2 {
    int value;
    Box_ReferenceEqualityOperators_2(int value) {
        this.value = value;
    }
}
