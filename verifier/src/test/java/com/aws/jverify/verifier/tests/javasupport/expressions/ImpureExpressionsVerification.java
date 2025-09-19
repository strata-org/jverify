package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 5, dafnyErrors = 0)
public class ImpureExpressionsVerification {
    int nestedImpureExpression() {
        if (impureM(1) == 1) {
            var x = impureM(impureM(2));
        }
        return impureM(3);
    }
    
    @SuppressWarnings("ConstantValue")
    void nestedAssignment() {
        var x = 3;
        if ((x += 1) == 4) {
            var y = 3;
        }
        if (x++ == 4) {
            var y = 3;
        }
    }
    
    // impure because it is not marked as Pure
    int impureM(int input) {
        return input;
    }
}
