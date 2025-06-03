package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 4, dafnyVerified = 10, dafnyErrors = 4)
public class MixedExternalAndInternalContract {
    public void root() {
        MixedContractExtender extender = new MixedContractExtender();
        var i = extender.internalContract();
        check(i > 4);
        var e = extender.externalContract(3);
        check(e > 3);
        var a = extender.addedMethod();
        check(a == 1);
    }
}

@Contract(MixedContract.class)
class MixedContractContract implements MixedContract {
    @Override
    public int externalContract(int x) {
        postcondition((Integer r) -> r > 4);
        return 5;
    }

    @Override
    public int redeclaredContract() {
        postcondition((Integer r) -> true);
        throw new ContractException();
    }

    void dummyError() {
        postcondition(false);
    }
}

interface MixedContract {
    
    int externalContract(int x);
    default int internalContract() {
        postcondition((Integer r) -> r > 2);
        return 1;
//             ^ error
    }
    
    default int redeclaredContract() {
        postcondition((Integer r) -> r > 2);
        return 1;
//             ^ error
    }
}

class MixedContractExtender implements MixedContract {
    @Override
    public int externalContract(int x) {
        return 5;
    }
    
    @Pure
    public int addedMethod() {
        return 1;
    }
}
