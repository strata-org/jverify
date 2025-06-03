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
//         ^ error: Method '<init>' is part of a @Contract class, but it does not override any methods for which to provide a contract
    }
}