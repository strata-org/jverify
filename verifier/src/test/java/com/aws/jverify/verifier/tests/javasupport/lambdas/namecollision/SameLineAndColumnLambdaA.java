package com.aws.jverify.verifier.tests.javasupport.lambdas.namecollision;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 6, failedAssertions = 0, additionalFiles = {"./SameLineAndColumnLambdaB.java"})
public class SameLineAndColumnLambdaA {
    void foo() {
        useI(() -> {});
    }
    
    void useI(I i) {}
    
    interface I {
        void bar();
    }
}
