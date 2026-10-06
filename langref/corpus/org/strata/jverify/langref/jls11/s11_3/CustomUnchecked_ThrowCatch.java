package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 11.1.1 -- a user-defined subclass of RuntimeException is unchecked
// and can be thrown/caught without a throws declaration.
class CustomUnchecked_ThrowCatch {
    static class MyEx extends RuntimeException {
        MyEx(String m) { super(m); }
    }

    static void run() {
        String msg = null;
        try {
            throw new MyEx("custom");
        } catch (MyEx e) {
            msg = e.getMessage();
        }
        check(msg != null && msg.equals("custom"));
    }
}
