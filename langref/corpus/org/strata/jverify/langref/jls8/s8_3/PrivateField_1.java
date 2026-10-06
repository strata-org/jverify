package org.strata.jverify.langref.jls8.s8_3;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.1 -- private field access: a private static field is accessible from
// within the same class.
class PrivateField_1 {
    private static int secret = 42;

    static void run() {
        check(secret == 42);
    }
}
