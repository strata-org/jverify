package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

class Long {
    public void foo() {
        var l = 3L;
        var r = 3L;
        var v1 = l++;
        check(v1 == 3L);
        check(l == 4L);
        var v2 = l--;
        check(v2 == 4L);
        check(l == 3L);
        var v3 = ++l;
        check(v3 == 4L);
        check(l == 4L);
        var v4 = --l;
        check(v4 == 3L);
        check(l == 3L);
        var v5 = l * r;
        check(v5 == 9L);
        var v6 = l / r;
        check(v6 == 1L);
        var v7 = l % 2L;
        check(v7 == 1L);
        var v8 = l + r;
        check(v8 == 6L);
        var v9 = l - r;
        check(v9 == 0);
        var v10 = l < r;
        check(!v10);
        var v11 = l > r;
        check(!v11);
        var v12 = l <= r;
        check(v12);
        var v13 = l >= r;
        check(v13);
        l += r;
        l -= r;
        l *= r;
        l /= r;
        l %= r;
    } 
}
