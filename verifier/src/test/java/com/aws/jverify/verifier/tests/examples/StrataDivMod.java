package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class StrataDivMod {
    static void truncationDivision() {
        check(-7 / 2 == -3);
        check(-7 % 2 == -1);
        check(7 / 2 == 3);
        check(7 % 2 == 1);
    }
}
