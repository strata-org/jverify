package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

@SuppressWarnings("ConstantValue")
class VerifyLong {
    public void foo() {
        var l = 3L;
        var r = 3L;
        /*var incrementPostfix =*/ l++;
        // check(incrementPostfix == 3L);
        check(l == 4L);
        /*var decrementPostfix =*/ l--;
        //check(decrementPostfix == 4L);
        check(l == 3L);
        /*var incrementPrefix =*/ ++l;
        //check(incrementPrefix == 4L);
        check(l == 4L);
        /*var decrementPrefix =*/ --l;
        //check(decrementPrefix == 3L);
        check(l == 3L);
        
        
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
    } 
}
