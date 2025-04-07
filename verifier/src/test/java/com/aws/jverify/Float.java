package com.aws.jverify;

import com.aws.jverify.generated.LocalVariable;

class Float {
    public void foo() {
        var l = 3.0f;
        var r = 3.0f;
        var v1 = l++;
        var v2 = l--;
        var v3 = ++l;
        var v4 = --l;
        var v5 = l * r;
        var v6 = l / r;
        var v7 = l % r;
        var v8 = l + r;
        var v9 = l - r;
        var v10 = l - r;
        var v11 = l < r;
        var v12 = l > r;
        var v13 = l <= r;
        var v14 = l >= r;
        l += r;
        l -= r;
        l *= r;
        l /= r;
        l %= r;
    } 
}
