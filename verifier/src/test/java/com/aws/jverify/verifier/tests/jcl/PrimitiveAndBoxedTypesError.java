package com.aws.jverify.verifier.tests.jcl;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 2)
class PrimitiveAndBoxedTypesError {

    boolean referenceEqualityOnBoxedPrimitive(Integer i, Integer j) {
        //noinspection NumberEquality
        return i == j;
//               ^ error: '==' is only allowed when at least one operand's type is impure
    }
}
