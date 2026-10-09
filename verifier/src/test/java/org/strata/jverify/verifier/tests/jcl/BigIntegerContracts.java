// BigInteger.valueOf and signum apply their builtin contracts. The contract
// field is @Unbounded, so widening the long argument adds no obligation; the
// last method checks a sign the contract rules out.
package org.strata.jverify.verifier.tests.jcl;

import org.strata.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(useBuiltinContracts = true, exitCode = 4, methodsVerified = 2, methodsInvalid = 1, errorCount = 1)
class BigIntegerContracts {
    static int positiveSign(long x) {
        precondition(x > 0);
        BigInteger b = BigInteger.valueOf(x);
        int s = b.signum();
        check(s == 1);
        return s;
    }

    static int wrongSign(long x) {
        precondition(x > 0);
        BigInteger b = BigInteger.valueOf(x);
        int s = b.signum();
        check(s == 0);
//      ^^^^^^^^^^^^^^ Error: assertion does not hold
        return s;
    }
}
