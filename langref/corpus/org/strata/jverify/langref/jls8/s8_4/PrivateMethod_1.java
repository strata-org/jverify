package org.strata.jverify.langref.jls8.s8_4;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.3 -- private method access: a private static method is accessible from
// within the same class.
class PrivateMethod_1 {
    private static int hidden() {
        return 7;
    }

    static void run() {
        check(hidden() == 7);
    }
}
