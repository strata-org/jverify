package org.strata.jverify.verifier.tests.javasupport.packages.a;

import org.strata.jverify.Transparent;

public class Foo {
    @Transparent
    public int bar(int x) {
        return 1;
    }
}
