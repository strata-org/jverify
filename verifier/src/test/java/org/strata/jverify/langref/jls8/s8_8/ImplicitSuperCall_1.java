package org.strata.jverify.langref.jls8.s8_8;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.8.7 -- implicit superclass constructor invocation: if a constructor
// body does not begin with an explicit super(...) or this(...), the compiler
// inserts a super() call, so the superclass constructor still runs.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_8.ImplicitSuperCall_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class ImplicitSuperCall_1 {
    static int baseInit = 0;

    static class Base {
        Base() {
            baseInit = 99;
        }
    }

    static class Sub extends Base {
    }

    static void run() {
        baseInit = 0;
        new Sub();
        check(baseInit == 99);
    }
}
