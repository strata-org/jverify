package com.aws.jverify.verifier.tests.multipackage.a;

import com.aws.jverify.Pure;

public class Foo {
    @Pure
    public int bar() {
        return 1;
    }
}
