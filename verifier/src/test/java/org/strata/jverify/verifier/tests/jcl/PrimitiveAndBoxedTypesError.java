package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_DIAGNOSTIC}, exitCode = 2, useBuiltinContracts = true)
class PrimitiveAndBoxedTypesError {

    boolean referenceEqualityOnBoxedPrimitive(Integer i, Integer j) {
        //noinspection NumberEquality
        return i == j;
//               ^ error: '==' is only allowed when at least one operand's type is impure
    }
}
