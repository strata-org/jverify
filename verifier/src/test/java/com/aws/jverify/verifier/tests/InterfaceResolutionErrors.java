package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
class InterfaceResolutionErrors {}

interface I2 {
}

@Contract(I2.class)
class I2Contract {
    public I2Contract(int x) {
//         ^ error: a class that defines the contract for an interface may not have explicit constructors
    }
}