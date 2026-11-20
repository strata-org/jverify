package org.strata.jverify.verifier.tests.javasupport.packages.samename.b;

import org.strata.jverify.Transparent;

public class Foo {
    public int c;

    @Transparent
    public int get() {
        return 2;
    }
}
