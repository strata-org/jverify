package com.aws.jverify.examples;

import static com.aws.jverify.JVerify.*;
import com.aws.jverify.testengine.JVerifyTest;

// This test verifies that java_div correctly implements Java's truncation-toward-zero semantics
@JVerifyTest(methodsVerified = 2, errorCount = 0)
@SuppressWarnings("ConstantValue")
class DivisionBug {

    public static int divisionTest() {
        int result = -7 / 2;
        check(result == -3);  // Correct: Java's -7/2 = -3
        return result;
    }
}
