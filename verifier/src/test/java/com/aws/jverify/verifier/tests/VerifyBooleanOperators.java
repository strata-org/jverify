// TEST: exitCode=4 dafnyVerified=0 dafnyErrors=1

package com.aws.jverify.verifier.tests;

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
