// TEST: exitCode=0 dafnyVerified=1 dafnyErrors=0

package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest
public class Lambdas {

    public void repeatOneself() {
        doSomethingTwice(x -> System.out.println(x));
    }

    public void doSomethingTwice(SomethingDoer doer) {
        doer.doSomething(1);
        doer.doSomething(2);
    }
}

interface SomethingDoer {
    void doSomething(int x);
}

@Contract(SomethingDoer.class)
class SomethingDoerContract implements SomethingDoer {
    @Override
    public void doSomething(int x) {
        throw new ContractException();
    }
}