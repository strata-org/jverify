package com.aws.jverify.verifier.tests.javasupport.interfaces;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
public class InterfacesTranslation { }

interface HasNoContractMethod {
    @Pure
    int hasNoContract();
//      ^ error: method 'hasNoContract' without a body still needs a contract. Use @Contract to specify a contract class.
}
