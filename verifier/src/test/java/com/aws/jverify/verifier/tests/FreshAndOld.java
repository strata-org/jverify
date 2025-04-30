// TEST: exitCode=0 dafnyVerified=2 dafnyErrors=0

package com.aws.jverify.verifier.tests;

import static com.aws.jverify.JVerify.*;

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
        check(old(this).x == x);
//            ^^^^^^^^^ Argument to 'old' does not dereference the mutable heap, so this use of 'old' has no effect
        x = 3;
        check(old(this.x) != x);
    }
}
