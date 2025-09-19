package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 4, dafnyErrors = 0)
public class ImpureExpressions {
    void nestedImpureExpression() {
        var x = impure(impure(3));
    }
    
    // impure because it is not marked as Pure
    int impure(int input) {
        return 3;
    }
}
