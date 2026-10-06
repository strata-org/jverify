package org.strata.jverify.langref.jls3.s3_9;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: try / catch / finally / throw.
class Keywords_9 {
    static void run() {

        boolean caught = false;
        try {
            throw new RuntimeException();
        } catch (RuntimeException e) {
            caught = true;
        } finally {
        }
        check(caught);
    }
}
