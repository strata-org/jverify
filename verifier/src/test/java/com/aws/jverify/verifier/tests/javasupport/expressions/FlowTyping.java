package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 9, errorCount = 0)
public class FlowTyping {
    interface I {}
    interface J extends I {
        @Pure
        @Verify(false)
        default I anotherI() {
            throw new RuntimeException("Not implemented");
        }
        default int value() {
            return 3;
        }
    }
    interface G extends I {
        default int value() {
            return 0;
        }
    }
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
        if (i instanceof J j && j.anotherI() instanceof G g) {
            return j.value() + g.value() == 3;
        }
        return true;
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
