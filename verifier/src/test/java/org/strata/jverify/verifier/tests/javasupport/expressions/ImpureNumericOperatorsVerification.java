package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

/**
 * Numeric operators are those that apply to the types:
 * byte, short, int, long, float, double, char
 */
@SuppressWarnings("ConstantValue")
@JVerifyTest(methodsVerified = 1, errorCount = 0)
class ImpureNumericOperatorsVerification {
    public int foo() {
        var l = 3;
        var r = 3;
        var incrementPostfix = l++;
        check(incrementPostfix == 4);
        check(l == 4L);
        var decrementPostfix = l--;
        check(decrementPostfix == 3);
        check(l == 3L);
        var incrementPrefix = ++l;
        check(incrementPrefix == 4);
        check(l == 4);
        var decrementPrefix = --l;
        check(decrementPrefix == 3);
        check(l == 3);
        
        var plusEquals = l += r;
        var minEquals = l -= r;
        var mulEquals = l *= r;
        var divEquals = l /= r;
        var modEquals = l %= r;
        
        return 3;
    } 
}
