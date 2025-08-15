package com.aws.jverify.verifier.tests.javasupport.lambdas.namecollision;

public class SameLineAndColumnLambdaB {
    
    
    
    void foo() {
        useI(() -> {});
    } // This needs to be on the same line as this method in A.java
    
    void useI(I i) {}
    
    interface I {
        void bar();
    }
}
