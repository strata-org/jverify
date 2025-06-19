package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 2)
class ResolutionErrorsNameClash {

    int postconditionNameClash(int x) {
        postcondition((Integer res) -> res == x);
        int res;
//      ^^^^^^^^ Error: Duplicate local-variable name: res
        res = x;
        return res;
    }
}
