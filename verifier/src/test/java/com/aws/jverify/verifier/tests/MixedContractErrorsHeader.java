package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 2)
public class MixedContractErrorsHeader {
}

@Contract(MixedContractErrorsI.class)
class MixedContractErrorsC implements MixedContractErrorsI {

    @Override
    public int redeclaredContract() {
        postcondition((Integer r) -> true);
        throw new ContractException();
    }
}

interface MixedContractErrorsI {

    default int redeclaredContract() {
//              ^ error: method redeclaredContract has both an extern- and internally defined contract, which is not allowed.
        postcondition((Integer r) -> r > 2);
        return 1;
    }
}