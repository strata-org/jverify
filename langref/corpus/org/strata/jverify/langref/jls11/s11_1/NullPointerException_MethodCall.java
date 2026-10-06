package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- NullPointerException from a method call on a null receiver.
class NullPointerException_MethodCall {
    static void run() {
        Object o = null;
        boolean caught = false;
        try {
            o.hashCode();
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}
