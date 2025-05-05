// TEST: exitCode=0 dafnyVerified=0 dafnyErrors=0

package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest
public class Lambdas {

    public void doSomethingTwice(SomethingDoer doer) {
        doer.doSomething(1);
        doer.doSomething(2);
    }
}

interface SomethingDoer {
    public int doSomething(int x);
}