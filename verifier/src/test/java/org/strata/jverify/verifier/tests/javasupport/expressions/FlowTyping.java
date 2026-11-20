package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.Transparent;
import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 5, errorCount = 0)
public class FlowTyping {
    interface I {}
    interface J extends I {
        @Transparent
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
//      ^ warning: instance method 'ternaryFlow' was not verified: instanceof on opaque reference types is not yet supported
        return i instanceof C c ? c.x : 3;  
    }
    
    int ifThenElseFlow(I i) {
//      ^ warning: instance method 'ifThenElseFlow' was not verified: instanceof on opaque reference types is not yet supported
        if (i instanceof C c) {
            return c.x;
        }
        return 3;
    }
    
    boolean andOperatorFlow(I i) {
//          ^ warning: instance method 'andOperatorFlow' was not verified: instanceof on opaque reference types is not yet supported
        if (i instanceof J j && j.anotherI() instanceof G g) {
            return j.value() + g.value() == 3;
        }
        return true;
    }
    
    int nested(I i) {
//      ^ warning: instance method 'nested' was not verified: instanceof on opaque reference types is not yet supported
        if (i instanceof C c) {
            if (c instanceof D d) {
                return d.y;
            }
            return c.x;
        }
        return 3;
    }
    
}
