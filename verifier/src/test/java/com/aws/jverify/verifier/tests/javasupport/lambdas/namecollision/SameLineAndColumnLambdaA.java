package com.aws.jverify.verifier.tests.javasupport.lambdas.namecollision;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 0, dafnyVerified = 9, dafnyErrors = 0)
public class SameLineAndColumnLambdaA {
    void foo() {
        useI(() -> {});
    }
    
    void useI(I i) {}
    
    interface I {
        void bar();
    }
}
