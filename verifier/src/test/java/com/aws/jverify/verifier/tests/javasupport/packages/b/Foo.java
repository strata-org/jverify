package com.aws.jverify.verifier.tests.javasupport.packages.b;

import com.aws.jverify.Pure;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.precondition;

public class Foo {
    @Pure
    public int bar(int x) {
        precondition(x > 2);
        return 2;
    }
    
    void fails() {
        check(false);
    }
}
