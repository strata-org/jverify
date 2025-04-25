// TEST: exitCode=4 dafnyVerified=0 dafnyErrors=1

package com.aws.jverify.verifier.tests;

import static com.aws.jverify.JVerify.check;

/**
 * Numeric operators are those that apply to the types:
 * byte, short, int, long, float, double, char
 */
@SuppressWarnings("ConstantValue")
class VerifyNumericOperators {
    public void foo() {
        var l = 3L;
        var r = 3L;
        l++;
        check(l == 4L);
        l--;
        check(l == 3L);
        ++l;
        check(l == 4L);
        --l;
        check(l == 3L);

        var positive = +l;
        check(positive == 3);

        var negation = -l;
        check(negation == -3);
        
        var multiplication = l * r;
        check(multiplication == 9L);
        var division = l / r;
        check(division == 1L);
        var remainder = l % 2L;
        check(remainder == 1L);
        
        var addition = l + r;
        check(addition == 6L);
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
        check(l == 6L);
        l -= r;
        check(l == 3L);
        l *= r;
        check(l == 9L);
        l /= r;
        check(l == 3L);
        l %= 2L;
        check(l == 1L);
        
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    } 
}
