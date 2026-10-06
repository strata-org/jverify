package org.strata.jverify.langref.jls8.s8_3;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1.1 -- static Fields: a static field is shared across all instances of the class.
class StaticField {
    static int counter = 0;

    static void run() {
        StaticField.counter = 5;
        StaticField.counter = StaticField.counter + 3;
        check(StaticField.counter == 8);
    }
}
