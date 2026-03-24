package com.aws.jverify.verifier.tests.examples;

import static com.aws.jverify.JVerify.*;

class StrataNameCollisionHelper {
    static int compute(int x) {
        precondition(0 <= x && x <= 1000);
        return x + 1;
    }
}
