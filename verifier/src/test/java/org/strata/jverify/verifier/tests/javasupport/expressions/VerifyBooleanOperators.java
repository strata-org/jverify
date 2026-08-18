package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;

@SuppressWarnings({"ConstantValue", "PointlessBooleanExpression"})
@JVerifyTest(exitCode = 4, methodsVerified = 1, errorCount = 1)
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
//      ^^^^^^^^^^^^ Error: assertion does not hold
    } 
}
