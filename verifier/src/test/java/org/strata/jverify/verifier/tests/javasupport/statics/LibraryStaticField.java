package org.strata.jverify.verifier.tests.javasupport.statics;

import org.strata.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static org.strata.jverify.JVerify.*;

/**
 * A static field of a library class is a global with an arbitrary value: no
 * initializer of the class is translated, and no contract states the value.
 * Reading it is allowed; what the value is cannot be proved.
 */
@JVerifyTest(useBuiltinContracts = true, continueOnErrors = true, exitCode = 4, errorCount = 1)
class LibraryStaticField {

    static BigInteger zero() {
        return BigInteger.ZERO;
    }

    static void sameFieldTwice() {
        BigInteger a = BigInteger.ZERO;
        BigInteger b = BigInteger.ZERO;
        check(a == b);
    }

    static void valueIsNotKnown() {
        check(BigInteger.ZERO.signum() == 0);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion does not hold
    }
}
