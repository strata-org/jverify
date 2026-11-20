package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

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

        // Mixed int-left / double-right: binary numeric promotion still yields a
        // floating-point remainder, so it must be rejected even though the LEFT
        // operand is int (a check that inspected only the left type missed this).
        var i = 3;
        var mixedRemainder = i % 2.0;
//                             ^ error: modulo operator (%) with floating-point types is not supported
        i %= 2.0;
//        ^ error: modulo operator (%) with floating-point types is not supported
    }
}
