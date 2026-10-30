package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

/**
 * The known division-overflow gap: {@code Integer.MIN_VALUE
 * / -1} wraps to {@code MIN_VALUE} in Java, but the mathematical quotient is
 * {@code 2^31}, out of int range — and unlike {@code +}/{@code -}/{@code *},
 * division carries no overflow obligation at the operation (it is entangled
 * with the implicit-ArithmeticException machinery). An intermediate quotient
 * that lands back in range before reaching a checked position therefore
 * still verifies against the mathematical value: the {@code check} below is
 * proved, yet Java computes {@code MIN_VALUE / 2}, not {@code 2^30}.
 *
 * <p>Expected to fail until division gets its overflow obligation; the
 * desired diagnostic is the marker below.
 */
@JVerifyTest(exitCode = 4, errorCount = 1,
        expectedToFail = true, blockedBy = {Blocker.FRONTEND_OTHER})
class DivisionOverflow {
    static int halfQuotient(int a, int b) {
        precondition(a == Integer.MIN_VALUE && b == -1);
        int r = (a / b) / 2;
//              ^^^^^^^ Error: no int overflow on '/' does not hold
        check(r == 1073741824);
        return r;
    }
}
