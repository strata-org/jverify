package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 6, dafnyErrors = 0)
public class FlowTyping {
    interface I {}
    static class C implements I {
        int x;
    }
    
    int ternaryFlow(I i) {
        return i instanceof C c ? c.x : 3;  
    }
    
    int ifThenElseFlow(I i) {
        if (i instanceof C c) {
            return c.x;
        }
        return 3;
    }
    
}
