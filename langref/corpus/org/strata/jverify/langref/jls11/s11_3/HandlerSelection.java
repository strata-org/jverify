package org.strata.jverify.langref.jls11.s11_3;

import static org.strata.jverify.JVerify.*;

// JLS 11.3 -- Run-Time Handling: among several catch clauses, the first one in
// textual order whose parameter type is assignable from the thrown exception's
// run-time class is selected; later matching clauses are not executed.
class HandlerSelection {
    static void run() {
        int which = 0;
        try {
            throw new IllegalStateException("state");
        } catch (IllegalStateException e) {
            which = 1;          // most specific matching clause wins
        } catch (RuntimeException e) {
            which = 2;          // would also match, but is shadowed above
        }
        check(which == 1);
    }
}
