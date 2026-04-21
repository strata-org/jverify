package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 2, useBuiltinContracts = true)
class PrimitiveAndBoxedTypesError {

    boolean referenceEqualityOnBoxedPrimitive(Integer i, Integer j) {
        //noinspection NumberEquality
        return i == j;
//               ^ error: '==' is only allowed when at least one operand's type is impure
    }
}
