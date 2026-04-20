package org.strata.jverify.verifier.tests.javasupport.packages.c;

import org.strata.jverify.Pure;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;

public class Foo {
    void fails() {
        check(false);
    }
}
