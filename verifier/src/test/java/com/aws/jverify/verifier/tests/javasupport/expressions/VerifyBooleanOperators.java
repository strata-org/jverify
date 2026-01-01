package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@SuppressWarnings({"ConstantValue", "PointlessBooleanExpression"})
@JVerifyTest(exitCode = 4, javaVerified = 1, javaErrors = 1)
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
//      ^^^^^^^^^^^^ Error: assertion could not be proved
    } 
}
