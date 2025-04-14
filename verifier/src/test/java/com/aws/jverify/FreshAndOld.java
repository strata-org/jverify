package com.aws.jverify;

import static com.aws.jverify.JVerify.*;

class FreshAndOld {
    int x;
    
    void freshTest() {
        var c = new Object();
        check(fresh(c));
        check(!fresh(this));
        check(!fresh(c, this));
    }
    
    void oldTest() {
        modifies(this);

        check(old(this.x) == x);
        check(old(this).x == x);
        x = 3;
        check(old(this.x) != x);
        check(old(this).x != x);
    }
}
