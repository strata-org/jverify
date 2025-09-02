package com.aws.jverify.verifier.tests.javasupport.lambdas.namecollision;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 16, dafnyErrors = 0, additionalFiles = {"./SameLineAndColumnLambdaB.java"})
public class SameLineAndColumnLambdaA {
    void foo() {
        useI(() -> {});
    }
    
    void useI(I i) {}
    
    interface I {
        void bar();
    }
}
