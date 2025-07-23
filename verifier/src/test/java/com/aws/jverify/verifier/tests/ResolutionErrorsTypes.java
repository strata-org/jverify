package com.aws.jverify.verifier.tests;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 1, dafnyErrors = 1, useBuiltinContracts = true)
class ResolutionErrorsTypes { // TODO test name is incorrect
    @Pure
    static boolean boxedIsZero(Integer i) {
        return i == 0;
    }

    @SuppressWarnings("ConstantValue")
    static void nullArgForNonNullParam() {
        Integer zero = null;
//                     ^^^^ Error: value of expression (of type 'Integer?') is not known to be an instance of type 'Integer', because it might be null
        check(boxedIsZero(zero));
    }
}
