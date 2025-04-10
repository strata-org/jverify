package com.aws.jverify;

import static com.aws.jverify.JVerify.check;

@SuppressWarnings({"ConstantValue", "PointlessBooleanExpression"})
class VerifyBooleanOperators {
    public void foo() {
        var p = true;
        var q = false;
        var and = p && q;
        check(and == false);
        var or = p || q;
        check(or == true);
        var not = !p;
        check(not == false);
        
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    } 
}
