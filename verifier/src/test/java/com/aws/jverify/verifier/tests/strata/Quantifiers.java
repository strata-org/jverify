package com.aws.jverify.verifier.tests.strata;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, methodsVerified = 3, errorCount = 0, BACKENDS = {Strata})
class Quantifiers {
    static void testForallWithImplies() {
        check(forall((int i) -> implies(i > 0, i >= 1)));
    }

    static void testExists() {
        check(exists((int i) -> i == 42));
    }
}
