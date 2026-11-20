package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.Transparent;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(exitCode = 2, useBuiltinContracts = true)
class PrimitiveAndBoxedTypesError {

    boolean referenceEqualityOnBoxedPrimitive(Integer i, Integer j) {
        //noinspection NumberEquality
        return i == j;
//               ^ error: '==' on boxed values is not yet supported: their identity depends on the box cache
    }
}
