package org.strata.jverify.verifier.tests.javasupport.expressions;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(methodsVerified = 3, errorCount = 0)
class FreshAndOld {
    int x;
    
    class Anything {}
    void freshTest() {
//       ^ warning: instance method 'freshTest' was not verified: Unsupported JVerify method: fresh
        var c = new Anything();
        check(fresh(c));
        check(!fresh(this));
    }
    
    void oldTest() {
        modifies(this);
        precondition(x == 1);

        check(old(this.x) == x);
        x = 3;
        check(old(this.x) != x);
    }
}
