package org.strata.jverify.langref.jls4.s4_3;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.4 -- When Reference Types Are the Same: two type names denoting the same type
// are interchangeable; a value assigned through one name is the same object through the other.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls4.s4_3.WhenReferenceTypesAreTheSame_1#run")
class WhenReferenceTypesAreTheSame_1 {
    int tag;

    WhenReferenceTypesAreTheSame_1(int t) {
        this.tag = t;
    }

    static void run() {

        // The simple name and fully qualified name denote the same reference type, so
        // a reference of each kind may hold the same object without conversion.
        WhenReferenceTypesAreTheSame_1 x = new WhenReferenceTypesAreTheSame_1(3);
        org.strata.jverify.langref.jls4.s4_3.WhenReferenceTypesAreTheSame_1 y = x;
        check(x == y);
    }
}
