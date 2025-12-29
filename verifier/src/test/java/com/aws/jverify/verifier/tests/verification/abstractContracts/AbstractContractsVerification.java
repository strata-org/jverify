package com.aws.jverify.verifier.tests.verification.abstractContracts;

import com.aws.jverify.testengine.JVerifyTest;

import javax.xml.XMLConstants;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, dafnyVerified = 27, dafnyErrors = 2)
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
        public int foo(int x) {
            precondition(isAbstractBoolean());
            return 1;
        }
    }
    static class Mid extends Inner {
        @Override
        public int foo(int x) {
            precondition(isAbstractBoolean());
            postcondition((int r) -> r > 1);
            return 2;
        }
    }
    static class Outer extends Mid {
        @Override
        public int foo(int x) {
            precondition(x > 2);
            postcondition((int r) -> r > 2);
            check(x > 1);
            return 3;
        }
    }
    
    static class OuterWithoutPrecondition extends Mid {
        @Override
        public int foo(int x) {
            postcondition((int r) -> r > 2);
            return 3;
        }
    }

    int outerWithoutPreconditionConsumer(OuterWithoutPrecondition outer, int x) {
        return midConsumer(outer, x);
    }
    
    void testPreconditionOfOnMultiplePreconditions(int x) {
        if (x > 0) {
            usePreconditionOfOnMultiplePreconditions(0);
//          ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: a precondition for this call could not be proved
        }
        usePreconditionOfOnMultiplePreconditions(3);
        usePreconditionOfOnMultiplePreconditions(11);
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: a precondition for this call could not be proved
    }

    void usePreconditionOfOnMultiplePreconditions(int x) {
        precondition(preconditionOf(multiplePreconditions(x)));
//                                                        ^ Related location: this is the precondition that could not be proved
//                                                        ^ Related location: this is the precondition that could not be proved
    }

    int multiplePreconditions(int x) {
        precondition(x > 2);
        precondition(x < 10);
        return 3;
    }
}
