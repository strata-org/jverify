package org.strata.jverify.langref.jls8.s8_3;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.2 -- Field Initialization: a field declarator may include a variable initializer,
// evaluated when the declaration is executed (instance) or the class is initialized (static);
// fields without an initializer take the default value for their type.
class FieldInitialization_2 {
    int initialized = 3 + 4;
    int defaulted;
    static int staticInitialized = 100;

    static void run() {

        FieldInitialization_2 f = new FieldInitialization_2();
        check(f.defaulted == 0);
    }
}
