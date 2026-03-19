package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, BACKENDS = { Strata })
class StrataDivMod {
    static void truncationDivision() {
        check(-7 / 2 == -3);
        check(-7 % 2 == -1);
        check(7 / 2 == 3);
        check(7 % 2 == 1);
    }
}
