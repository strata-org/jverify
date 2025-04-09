package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

/**
 * Numeric operators are those that apply to the types:
 * byte, short, int, long, float, double, char
 */
@SuppressWarnings("ConstantValue")
class VerifyFloatingPointOperators {
    public void foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold

        var l = 3f;
        var r = 3f;
        l++;
        check(l == 4f);
        l--;
        check(l == 3f);
        ++l;
        check(l == 4f);
        --l;
        check(l == 3f);
        
        var multiplication = l * r;
        check(multiplication == 9f);
        var division = l / r;
        check(division == 1f);
        var remainder = l % 2f;
        check(remainder == 1f);
        
        var addition = l + r;
        check(addition == 6f);
        var subtraction = l - r;
        check(subtraction == 0);
        
        var lessThan = l < r;
        check(!lessThan);
        var greaterThan = l > r;
        check(!greaterThan);
        var lessThanOrEquals = l <= r;
        check(lessThanOrEquals);
        var greaterThanOrEquals = l >= r;
        check(greaterThanOrEquals);
        
        l += r;
        check(l == 6f);
        l -= r;
        check(l == 3f);
        l *= r;
        check(l == 9f);
        l /= r;
        check(l == 3f);
        l %= 2L;
        check(l == 1f);
    } 
}
