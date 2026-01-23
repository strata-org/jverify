package com.aws.jverify.verifier.tests.strata;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

@JVerifyTest(exitCode = 0, methodsVerified = 3, errorCount = 0, BACKENDS = {Strata})
class Expressions {
    static void testArithmetic() {
        int x = 5;
        int y = 3;
        check(x + y == 8);
        check(x - y == 2);
        check(x * y == 15);
        check(x / y == 1);
        check(x % y == 2);
        check(x > y);
        check(y < x);
        check(x >= 5);
        check(y <= 3);
        check(x != y);
    }

    static void testUnaryAndLogical() {
        int x = 5;
        check(-x == -5);
        check(!(x < 0));
        check(x > 0 || x < 0 || x == 0);
    }
}
