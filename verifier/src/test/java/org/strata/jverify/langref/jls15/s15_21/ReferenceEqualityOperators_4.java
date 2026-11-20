package org.strata.jverify.langref.jls15.s15_21;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators == and !=: identity comparison of object references
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_21.ReferenceEqualityOperators_4#run")
class ReferenceEqualityOperators_4 {
    static void run() {

        Box_ReferenceEqualityOperators_4 a = new Box_ReferenceEqualityOperators_4(1);
        Box_ReferenceEqualityOperators_4 c = a;
        check(!(a != c));
    }
}

class Box_ReferenceEqualityOperators_4 {
    int value;
    Box_ReferenceEqualityOperators_4(int value) {
        this.value = value;
    }
}
