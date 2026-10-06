package org.strata.jverify.langref.jls3.s3_9;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: the 'throws' clause on a method declaration.
class Keywords_14 {
    static void run() {

        check(m() == 1);
    }

    static int m() throws RuntimeException {
        return 1;
    }
}
