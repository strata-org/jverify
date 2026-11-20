package org.strata.jverify.langref.jls15.s15_21;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.21.3 -- Reference Equality Operators == and !=: identity comparison of object references
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_21.ReferenceEqualityOperators_1#run")
class ReferenceEqualityOperators_1 {
    static void run() {

        Box_ReferenceEqualityOperators_1 a = new Box_ReferenceEqualityOperators_1(1);
        Box_ReferenceEqualityOperators_1 b = new Box_ReferenceEqualityOperators_1(1);

        // distinct objects: same contents but different identity
        check(a != b);
    }
}

class Box_ReferenceEqualityOperators_1 {
    int value;
    Box_ReferenceEqualityOperators_1(int value) {
        this.value = value;
    }
}
