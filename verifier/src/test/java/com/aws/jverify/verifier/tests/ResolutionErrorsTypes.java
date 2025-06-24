package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 2)
class ResolutionErrorsTypes {
    @Pure
    static boolean boxedIsZero(Integer i) {
        return i == 0;
    }

    @SuppressWarnings("ConstantValue")
    static void nullArgForNonNullParam() {
        Integer zero = null;
//                     ^^^^ Error: type of 'null' is a reference type, but it is used as int
        check(boxedIsZero(zero));
    }
}
