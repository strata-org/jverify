package com.aws.jverify.verifier.tests.jcl;

import com.aws.jverify.Nullable;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, javaVerified = 3, javaErrors = 1, useBuiltinContracts = true)
class PrimitiveAndBoxedTypesVerification {
    @Pure
    static boolean boxedIsZero(Integer i) {
        return i == 0;
    }

    @SuppressWarnings("ConstantValue")
    static void useZero() {
        Integer zero = 0;
        check(boxedIsZero(zero));
    }

    @SuppressWarnings("ConstantValue")
    static void nullArgForNonNullParam() {
        Integer zero = null;
//                     ^^^^ Error: value of expression (of type 'Integer?') is not known to be an instance of type 'Integer', because it could not be proved to be non-null
    }
}
