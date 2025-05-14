package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
class FreshAndOld {
    int x;
    
    void freshTest() {
        var c = new Object();
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
