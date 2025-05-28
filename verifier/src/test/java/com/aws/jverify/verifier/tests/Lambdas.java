package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(dafnyVerified = 10, dafnyErrors = 0)
public class Lambdas {

    public void repeatOneself() {
        doSomethingTwice((x, y) -> {
            postcondition((Integer r) -> r == x);
            return x;
        });
        SomethingDoer doer = (x, y) -> {
            postcondition((Integer r) -> r == x);
            return x;
        };
        SomethingDoer doer2 = (x, y) -> {
            postcondition((Integer r) -> r == x);
            return x;
        };
        check(doer != doer2);
    }

    public void doSomethingTwice(SomethingDoer doer) {
        var y = doer.doSomething(1, 2);
        y = doer.doSomething(2, 3);
    }
}

interface SomethingDoer {
    int doSomething(int x, int y);
}

@Contract(SomethingDoer.class)
class SomethingDoerContract implements SomethingDoer {
    @Override
    public int doSomething(int x, int y) {
        postcondition((Integer r) -> r == x);
        throw new ContractException();
    }
}

class TimesTwo implements SomethingDoer {
    @Override
    public int doSomething(int x, int y) {
        postcondition((Integer r) -> r == x);
        return x;
    }
}
