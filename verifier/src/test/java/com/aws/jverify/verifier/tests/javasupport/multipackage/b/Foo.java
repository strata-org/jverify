package com.aws.jverify.verifier.tests.javasupport.multipackage.b;

import com.aws.jverify.Pure;

import static com.aws.jverify.JVerify.precondition;

public class Foo {
    @Pure
    public int bar(int x) {
        precondition(x > 2);
        return 2;
    }
}
