package org.strata.jverify.langref.jls8.s8_3;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.3 -- qualified field access: when a formal parameter shadows an
// instance field, `this.x` references the field and `x` references the
// parameter.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_3.FieldQualifiedAccess_1#run")
class FieldQualifiedAccess_1 {
    static class C {
        int x = 1;

        int m(int x) {
            return this.x + x;
        }
    }

    static void run() {
        check(new C().m(10) == 11);
    }
}
