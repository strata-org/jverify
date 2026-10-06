package org.strata.jverify.langref.jls15.s15_21;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators == and !=: identity comparison of object references
class ReferenceEqualityOperators_3 {
    static void run() {

        Box_ReferenceEqualityOperators_3 a = new Box_ReferenceEqualityOperators_3(1);
        Box_ReferenceEqualityOperators_3 c = a;

        // aliasing: c refers to the same object as a
        check(a == c);
    }
}

class Box_ReferenceEqualityOperators_3 {
    int value;
    Box_ReferenceEqualityOperators_3(int value) {
        this.value = value;
    }
}
