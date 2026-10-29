package org.strata.jverify.langref.jls8.s8_3;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.3.3 -- forward-reference-safe field initializer: an instance-field
// initializer may reference an earlier-declared instance field, because
// initializers run in textual order.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_3.FieldInitialization_ReferenceEarlier#run")
class FieldInitialization_ReferenceEarlier {
    static class C {
        int a = 5;
        int b = a + 1;
    }

    static void run() {
        check(new C().b == 6);
    }
}
