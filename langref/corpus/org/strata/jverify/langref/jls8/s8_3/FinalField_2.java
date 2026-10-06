package org.strata.jverify.langref.jls8.s8_3;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1.2 -- final Fields: a final field can be assigned only once, in a declarator or constructor.
class FinalField_2 {
    final int constant;
    static final int SHARED = 42;

    FinalField_2(int v) {
        this.constant = v;
    }

    static void run() {

        check(SHARED == 42);
    }
}
