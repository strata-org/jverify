package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

@SuppressWarnings("ConstantValue")
class ResolutionErrorsBooleanOperators {
    public void foo() {
        var p = true;
        var q = false;

        var and = p &= q;
//                  ^ error:
        var or = p |= q;
//                  ^ error:
        var xor = p ^= q;
//                  ^ error:
    } 
}
