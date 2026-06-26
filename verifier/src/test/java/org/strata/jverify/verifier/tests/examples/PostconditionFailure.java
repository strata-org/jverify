package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, errorCount = 1)
class PostconditionFailure {
    static int alwaysZero(int x) {
        postcondition((int res) -> res > 0);
//                                 ^^^^^^^ Error: postcondition does not hold
        return 0;
    }
}
