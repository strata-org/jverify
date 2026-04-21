package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 3, errorCount = 1, useBuiltinContracts = true)
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
