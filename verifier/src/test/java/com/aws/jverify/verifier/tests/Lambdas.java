// TEST: exitCode=0

package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.testengine.JVerifyTest;

import java.sql.Time;
import java.util.List;

@JVerifyTest
public class Lambdas {

    public void repeatOneself() {
        var doer = new TimesTwo();
        doSomethingTwice((x, y) -> {
            return x;
        });
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
        throw new ContractException();
    }
}

class TimesTwo implements SomethingDoer {
    @Override
    public int doSomething(int x, int y) {
        return x;
    }
}