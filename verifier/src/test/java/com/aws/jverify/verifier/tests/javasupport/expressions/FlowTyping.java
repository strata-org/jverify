package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 10, dafnyErrors = 0)
public class FlowTyping {
    interface I {}
    static class C implements I {
        int x;
    }
    static class D extends C {
        int y;
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
    
    boolean andOperatorFlow(I i) {
        return i instanceof C c && c.x == 3; 
    }
    
    int nested(I i) {
        if (i instanceof C c) {
            if (c instanceof D d) {
                return d.y;
            }
            return c.x;
        }
        return 3;
    }
    
}
