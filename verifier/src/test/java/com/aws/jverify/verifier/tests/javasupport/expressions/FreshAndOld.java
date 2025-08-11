package com.aws.jverify.verifier.tests.javasupport.expressions;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 6, dafnyErrors = 0)
class FreshAndOld {
    int x;
    
    class Anything {}
    void freshTest() {
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
