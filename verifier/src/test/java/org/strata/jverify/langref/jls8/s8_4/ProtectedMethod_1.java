package org.strata.jverify.langref.jls8.s8_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.4.3 -- protected method access: a protected method is inherited by
// subclasses and callable on subclass instances.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_4.ProtectedMethod_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
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
