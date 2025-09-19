package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

/**
 * Numeric operators are those that apply to the types:
 * byte, short, int, long, float, double, char
 */
@SuppressWarnings("ConstantValue")
@JVerifyTest(dafnyVerified = 3, dafnyErrors = 0)
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
