package org.strata.jverify.verifier.tests.javasupport.interfaces;

import org.strata.jverify.Contract;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
class InterfaceResolutionErrors {}

interface I2 {
}

@Contract(I2.class)
class I2Contract {
    public void foo(int x) {
//              ^ error: method 'foo' is part of a @Contract class, but its signature does not match any method from the contractee
    }
}