package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)  // Compilation errors due to unsupported modulo operator
@SuppressWarnings("ConstantValue")
class DoubleOperators {
    public void foo() {
        var l = 3.0;
        var r = 3.0;

        var equals = l == r;
        var notEquals = l != r;

        l++;
        l--;
        ++l;
        --l;

        var multiplication = l * r;
        var division = l / r;
        var remainder = l % 2d;
//                        ^ error: modulo operator (%) with floating-point types is not supported

        var addition = l + r;
        var subtraction = l - r;

        var lessThan = l < r;
        var greaterThan = l > r;
        var lessThanOrEquals = l <= r;
        var greaterThanOrEquals = l >= r;

        l += r;
        l -= r;
        l *= r;
        l /= r;
        l %= 2;
//        ^ error: modulo operator (%) with floating-point types is not supported
    }
}
