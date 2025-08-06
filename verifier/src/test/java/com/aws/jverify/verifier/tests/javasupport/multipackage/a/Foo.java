package com.aws.jverify.verifier.tests.javasupport.multipackage.a;

import com.aws.jverify.Pure;

public class Foo {
    @Pure
    public int bar(int x) {
        return 1;
    }
}
