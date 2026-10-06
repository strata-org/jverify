package org.strata.jverify.langref.jls11.s11_1;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.2 -- NullPointerException from dereferencing a null reference.
class NullPointerException_Deref {
    static void run() {
        String s = null;
        boolean caught = false;
        try {
            int x = s.length();
        } catch (NullPointerException e) {
            caught = true;
        }
        check(caught);
    }
}
