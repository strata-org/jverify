package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- IllegalArgumentException raised by a user-defined helper
// method and caught by the caller.
class IllegalArgumentException_UserThrow {
    static void reject() {
        throw new IllegalArgumentException("bad");
    }

    static void run() {
        boolean caught = false;
        try {
            reject();
        } catch (IllegalArgumentException e) {
            caught = true;
        }
        check(caught);
    }
}
