package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 4, dafnyErrors = 0)
public class ImpureExpressions {
    int nestedImpureExpression() {
        if (impureM(1) == 1) {
            var x = impureM(impureM(2));
        }
        return impureM(3);
    }
    
    // impure because it is not marked as Pure
    int impureM(int input) {
        return input;
    }
}
