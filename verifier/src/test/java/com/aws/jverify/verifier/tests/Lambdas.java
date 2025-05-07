// TEST: exitCode=0 dafnyVerified=1 dafnyErrors=0

package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.List;

@JVerifyTest
public class Lambdas {

    public void repeatOneself() {
        var p = new Printer();
        doSomethingTwice(p);
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

class Printer implements SomethingDoer {
    @Override
    public void doSomething(int x) {
//        System.out.println(x);
    }
}