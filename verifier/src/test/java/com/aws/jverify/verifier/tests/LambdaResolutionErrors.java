package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Modifiable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.modifies;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 2)
class LambdaResolutionErrors {

    public void repeatOneself() {
        NotCompatible doer = (x, y) -> { return x; };
    }
}

interface NotCompatible {
    int doSomething(int x, int y);
}

@Contract(NotCompatible.class)
@Modifiable
class NotCompatibleContract implements NotCompatible {
    @Override
    public int doSomething(int x, int y) {
        modifies(this);
        throw new ContractException();
    }
}