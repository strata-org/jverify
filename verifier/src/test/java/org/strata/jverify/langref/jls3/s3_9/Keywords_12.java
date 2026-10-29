package org.strata.jverify.langref.jls3.s3_9;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

// JLS 3.9 -- Keywords: the 'assert' statement is legal syntax
// (checked only when the JVM is started with -ea).
@JVerifyTest(interpret = true, interpreter = "laurel", entry = "org.strata.jverify.langref.jls3.s3_9.Keywords_12#run")
class Keywords_12 {
    static void run() {

        int x = 5;
        assert x == 5;
        check(x == 5);
    }
}
