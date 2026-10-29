package org.strata.jverify.langref.jls6.s6_4;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 6.4.1 -- Shadowing: even when a local variable shadows an instance
// field, the shadowed field remains accessible via `this.<name>`.
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls6.s6_4.Shadowing_2#run", expectedToFail = true, blockedBy = {Blocker.LANGREF_RESOLUTION_ERROR})
class Shadowing_2 {
    static void run() {
        check(new Holder2().m() == 1);
    }
}

class Holder2 {
    int x = 1;

    int m() {
        int x = 2; // shadows the field
        return this.x; // still reaches the field via `this`
    }
}
