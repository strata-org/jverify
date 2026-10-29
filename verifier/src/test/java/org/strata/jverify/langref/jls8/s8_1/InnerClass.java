package org.strata.jverify.langref.jls8.s8_1;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 8.1.3 -- Inner Classes and Enclosing Instances: a non-static inner class
// has an enclosing instance and can access the enclosing object's fields.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls8.s8_1.InnerClass#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_INTERP_ERROR})
class InnerClass {
    static void run() {
        Outer o = new Outer(10);
        Outer.Inner in = o.makeInner(5);
        check(in.sum() == 15);
    }
}

class Outer {
    private final int base;

    Outer(int base) {
        this.base = base;
    }

    Inner makeInner(int delta) {
        return new Inner(delta);
    }

    class Inner {
        private final int delta;

        Inner(int delta) {
            this.delta = delta;
        }

        int sum() {
            return base + delta;
        }
    }
}
