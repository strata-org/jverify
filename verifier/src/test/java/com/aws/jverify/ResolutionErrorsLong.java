package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

@SuppressWarnings("ConstantValue")
class ResolutionErrorsLong {
    public void foo() {
        var l = 3L;
        var r = 3L;
        var incrementPostfix = l++;
//                              ^ error: unary operator++(long) is not supported
        check(incrementPostfix == 3L);
        check(l == 4L);
        var decrementPostfix = l--;
//                              ^ error: unary operator--(long) is not supported
        check(decrementPostfix == 4L);
        check(l == 3L);
        var incrementPrefix = ++l;
//                            ^ error: unary operator++(long) is not supported
        check(incrementPrefix == 4L);
        check(l == 4L);
        var decrementPrefix = --l;
//                            ^ error: unary operator--(long) is not supported
        check(decrementPrefix == 3L);
        check(l == 3L);

        var bitwiseNot = ~l;
//                       ^ error: unary operator~(long) is not supported
        check(bitwiseNot != l);

        var plusEquals = l += r;
//                         ^ error: since '+=' performs mutation, it may only be used where a statement is allowed
        var minEquals = l -= r;
//                        ^ error: since '-=' performs mutation, it may only be used where a statement is allowed
        var mulEquals = l *= r;
//                        ^ error: since '*=' performs mutation, it may only be used where a statement is allowed
        var divEquals = l /= r;
//                        ^ error: since '/=' performs mutation, it may only be used where a statement is allowed
        var modEquals = l %= r;
//                        ^ error: since '%=' performs mutation, it may only be used where a statement is allowed
    } 
}
