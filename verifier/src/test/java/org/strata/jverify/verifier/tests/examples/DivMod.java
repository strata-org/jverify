package org.strata.jverify.verifier.tests.examples;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
class DivMod {
    static void truncationDivision() {
        check(-7 / 2 == -3);
        check(-7 % 2 == -1);
        check(7 / 2 == 3);
        check(7 % 2 == 1);
    }
}
