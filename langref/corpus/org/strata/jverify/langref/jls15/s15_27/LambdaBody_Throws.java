package org.strata.jverify.langref.jls15.s15_27;

import static org.strata.jverify.JVerify.*;

// JLS 15.27.2 -- Lambda Body: an unchecked exception thrown from within a
// lambda propagates out of the functional interface method invocation.
class LambdaBody_Throws {
    static void run() {

        Runnable r = () -> { throw new RuntimeException("x"); };
        boolean caught = false;
        try {
            r.run();
        } catch (RuntimeException e) {
            caught = true;
        }
        check(caught);
    }
}
