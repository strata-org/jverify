package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, errorCount = 1)
class StrataPostconditionFailure {
    static int alwaysZero(int x) {
        postcondition((int res) -> res > 0);
//                                 ^^^^^^^ Error: assertion does not hold
        return 0;
    }
}
