package org.strata.jverify.langref.jls6.s6_6;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.6.2 -- Details on protected Access: a protected member is accessible
// from any subclass of the class in which it is declared. Here `Sub` reads the
// protected field `v` inherited from `Base`.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_6.AccessProtected_1#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_UNSUPPORTED})
class AccessProtected_1 {
    static void run() {
        check(new Sub().read() == 5);
    }
}

class Base {
    protected int v = 5;
}

class Sub extends Base {
    int read() {
        return v;
    }
}
