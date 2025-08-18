package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
@SuppressWarnings("ConstantValue")
class FloatOperators {
    public void foo() {
        var l = 3f;
//              ^ error: float literals are not supported. Use double instead
        var r = 3f;
//              ^ error: float literals are not supported. Use double instead

        var equals = l == r;
        var notEquals = l != r;

        l++;
        //check(l == 4f);
        l--;
        //check(l == 3f);
        ++l;
        //check(l == 4f);
        --l;
        //check(l == 3f);

        var multiplication = l * r;
        //check(multiplication == 9f);
        var division = l / r;
        //check(division == 1f);
        var remainder = l % 2f;
//                        ^ error: modulo operator (%) with floating-point types is not supported
//                          ^ error: float literals are not supported. Use double instead
        //check(remainder == 1f);

        var addition = l + r;
        //check(addition == 6f);
        var subtraction = l - r;
        //check(subtraction == 0);

        var lessThan = l < r;
        //check(!lessThan);
        var greaterThan = l > r;
        //check(!greaterThan);
        var lessThanOrEquals = l <= r;
        //check(lessThanOrEquals);
        var greaterThanOrEquals = l >= r;
        //check(greaterThanOrEquals);

        l += r;
        //check(l == 6f);
        l -= r;
        //check(l == 3f);
        l *= r;
        //check(l == 9f);
        l /= r;
        //check(l == 3f);
        l %= 2;
        //check(l == 1f);
    }
}
