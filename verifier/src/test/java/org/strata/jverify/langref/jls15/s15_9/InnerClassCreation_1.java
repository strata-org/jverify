package org.strata.jverify.langref.jls15.s15_9;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 15.9 -- Class Instance Creation: qualified inner-class instantiation with
// an enclosing instance, using Primary.new InnerClass(...).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls15.s15_9.InnerClassCreation_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class InnerClassCreation_1 {
    static class Outer {
        int base = 10;
        class Inner {
            int m() { return base; }
        }
    }

    static void run() {

        Outer o = new Outer();
        Outer.Inner i = o.new Inner();
        check(i.m() == 10);
    }
}
