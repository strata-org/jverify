// ^ SameLineAndColumnLambdaB.java(7:10-7:11) warning: instance method 'foo' was not verified: polymorphic dispatch through interface/superclass is not yet supported
package org.strata.jverify.verifier.tests.javasupport.lambdas.namecollision;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 4, errorCount = 0, additionalFiles = {"./SameLineAndColumnLambdaB.java"})
public class SameLineAndColumnLambdaA {
    void foo() {
//       ^ warning: instance method 'foo' was not verified: polymorphic dispatch through interface/superclass is not yet supported
        useI(() -> {});
    }
    
    void useI(I i) {}
    
    interface I {
        void bar();
    }
}
