package org.strata.jverify.verifier.tests.javasupport.lambdas.namecollision;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 6, errorCount = 0, additionalFiles = {"./SameLineAndColumnLambdaB.java"})
public class SameLineAndColumnLambdaA {
    void foo() {
        useI(() -> {});
    }
    
    void useI(I i) {}
    
    interface I {
        void bar();
    }
}
