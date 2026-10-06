package org.strata.jverify.langref.jls4.s4_3;

import static org.strata.jverify.JVerify.*;

// JLS 4.3.4 -- When Reference Types Are the Same: two type names denoting the same type
// are interchangeable; a value assigned through one name is the same object through the other.
class WhenReferenceTypesAreTheSame_2 {
    int tag;

    WhenReferenceTypesAreTheSame_2(int t) {
        this.tag = t;
    }

    static void run() {

        // The simple name and fully qualified name denote the same reference type, so
        // a reference of each kind may hold the same object without conversion.
        WhenReferenceTypesAreTheSame_2 x = new WhenReferenceTypesAreTheSame_2(3);
        org.strata.jverify.langref.jls4.s4_3.WhenReferenceTypesAreTheSame_2 y = x;
        check(y.tag == 3);
    }
}
