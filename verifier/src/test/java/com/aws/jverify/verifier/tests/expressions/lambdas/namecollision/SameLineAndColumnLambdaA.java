package com.aws.jverify.verifier.tests.expressions.lambdas.namecollision;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 5, dafnyErrors = 0, additionalFiles = {"./SameLineAndColumnLambdaB.java"})
public class SameLineAndColumnLambdaA {
    void foo() {
        useI(() -> {});
    }
    
    void useI(I i) {}
    
    interface I {
        void bar();
    }
}
