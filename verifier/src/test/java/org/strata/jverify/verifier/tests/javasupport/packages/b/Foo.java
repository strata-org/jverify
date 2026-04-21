package org.strata.jverify.verifier.tests.javasupport.packages.b;

import org.strata.jverify.Pure;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;

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
