package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 11.1 -- Throwable.getMessage returns the detail message set at
// construction time.
class Exception_GetMessage {
    static void run() {
        RuntimeException e = new RuntimeException("hello");
        check(e.getMessage().equals("hello"));
    }
}
