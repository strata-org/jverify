package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

@SuppressWarnings("ConstantValue")
class ResolutionErrorLong {
    public void foo() {
        var l = 3L;
        var r = 3L;
        var incrementPostfix = l++;
        check(incrementPostfix == 3L);
        check(l == 4L);
        var decrementPostfix = l--;
        check(decrementPostfix == 4L);
        check(l == 3L);
        var incrementPrefix = ++l;
        check(incrementPrefix == 4L);
        check(l == 4L);
        var decrementPrefix = --l;
        check(decrementPrefix == 3L);
        check(l == 3L);

        var bitwiseNot = ~l;
        check(bitwiseNot != l);
    } 
}
