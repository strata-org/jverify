package com.aws.jverify.verifier.tests.contracts;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 2)
public class MixedContractErrorsTest {
}

@Contract(MixedContractErrorsI.class)
class MixedContractErrorsC implements MixedContractErrorsI {

    @Override
    public int redeclaredContract() {
//             ^ error: method 'redeclaredContract' already has an internally defined contract
        postcondition((Integer r) -> true);
        throw new ContractException();
    }
}

interface MixedContractErrorsI {

    default int redeclaredContract() {
        postcondition((Integer r) -> r > 2);
        return 1;
    }
}