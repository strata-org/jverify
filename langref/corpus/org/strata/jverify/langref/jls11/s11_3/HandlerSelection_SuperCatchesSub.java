package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 11.3 -- a catch clause whose parameter type is a supertype of the
// thrown exception's class handles that throw.
class HandlerSelection_SuperCatchesSub {
    static void run() {
        boolean caught = false;
        try {
            throw new IllegalStateException("state");
        } catch (RuntimeException e) {
            caught = true;
        }
        check(caught);
    }
}
