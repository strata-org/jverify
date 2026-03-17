package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, BACKENDS = { Strata })
class StrataQuantifiers {
    static void universalQuantifier() {
        check(forall((int x) -> implies(x > 0, x + 1 > 0)));
    }

    static void existentialQuantifier() {
        check(exists((int x) -> x > 100));
    }
}
