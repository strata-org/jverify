package com.aws.jverify.verifier.tests.expressions.lambdas.namecollision;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 4, dafnyVerified = 2, dafnyErrors = 1, additionalFiles = {"./B.java"})
public class SameLineAndColumnLambdaA {
    void foo() {
        useI(() -> {});
    }
    
    void useI(I i) {}
    
    interface I {
        void bar();
    }
}
