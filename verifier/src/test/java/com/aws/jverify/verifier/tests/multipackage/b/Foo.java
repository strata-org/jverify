package com.aws.jverify.verifier.tests.multipackage.b;

import com.aws.jverify.Pure;

public class Foo {
    @Pure
    public int bar() {
        return 2;
    }
}
