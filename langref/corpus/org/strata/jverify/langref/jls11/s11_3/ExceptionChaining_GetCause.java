package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 11.1 -- Throwable supports chaining: the (message, cause) constructor
// stores the cause, retrievable via getCause().
class ExceptionChaining_GetCause {
    static void run() {
        RuntimeException cause = new RuntimeException("inner");
        RuntimeException outer = new RuntimeException("outer", cause);
        check(outer.getCause() == cause);
    }
}
