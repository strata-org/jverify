package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@Contract(MixedContractErrors.class)
@JVerifyTest(exitCode = 4, dafnyVerified = 10, dafnyErrors = 4)
class MixedContractContractErrors implements MixedContractErrors {

    @Override
    public int redeclaredContract() {
//             ^ error
        postcondition((Integer r) -> true);
        throw new ContractException();
    }
}

interface MixedContractErrors {
    
    default int redeclaredContract() {
        postcondition((Integer r) -> r > 2);
        return 1;
    }
}
