package org.strata.jverify.langref.jls8.s8_4;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.3 -- protected method access: a protected method is inherited by
// subclasses and callable on subclass instances.
class ProtectedMethod_1 {
    static class Base {
        protected int m() {
            return 5;
        }
    }

    static class Sub extends Base {
    }

    static void run() {
        check(new Sub().m() == 5);
    }
}
