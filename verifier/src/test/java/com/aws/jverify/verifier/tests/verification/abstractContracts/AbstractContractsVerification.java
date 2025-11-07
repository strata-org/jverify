package com.aws.jverify.verifier.tests.verification.abstractContracts;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;

@JVerifyTest(dafnyVerified = 9, dafnyErrors = 0)
public class AbstractContractsVerification {
    int midConsumer(Mid mid, int x) {
        precondition(preconditionOf(mid.foo(x)));
        return mid.foo(x);
    }
    
    int outerConsumer(Outer outer, int x) {
        precondition(x > 2);
        return midConsumer(outer, x);
    }
    
    static class Inner {
        int foo(int x) {
            precondition(isAbstract());
            return 1;
        }
    }
    static class Mid extends Inner {
        @Override
        int foo(int x) {
            precondition(isAbstract());
            postcondition((int r) -> r == 10);
            return 2;
        }
    }
    static class Outer extends Mid {
        @Override
        int foo(int x) {
            precondition(x > 2);
            check(x > 1);
            return 3;
        }
    }
}
