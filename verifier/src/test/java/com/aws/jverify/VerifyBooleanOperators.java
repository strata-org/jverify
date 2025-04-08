package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

@SuppressWarnings("ConstantValue")
class VerifyBooleanOperators {
    public void foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold

        var p = true;
        var q = false;
        var and = p && q;
        var or = p || q;
        var not = !p;
        
        var bitwiseAnd = p & q;
        var bitwiseOr = p | q;
        var xor = p ^ q;
        
        p &= q;
        p |= q;
        p ^= q;
    } 
}
