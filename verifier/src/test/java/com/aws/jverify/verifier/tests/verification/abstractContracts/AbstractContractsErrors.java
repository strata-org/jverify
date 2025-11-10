package com.aws.jverify.verifier.tests.verification.abstractContracts;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 2)
public class AbstractContractsErrors {
    static class C {
        void foo() {
            postcondition((boolean)isAbstract());
//                                 ^ error: isAbstract() in clauses other than a preconditions is not supported
            decreases(isAbstract());
//                    ^ error: isAbstract() in clauses other than a preconditions is not supported
            reads(isAbstract());
//                ^ error: isAbstract() in clauses other than a preconditions is not supported
            modifies(isAbstract());
//                   ^ error: isAbstract() in clauses other than a preconditions is not supported
        }
        
        int noPrecondition() {
            return 1;
        }
        
        int twoPreconditions(int x) {
            precondition(x > 0);
            precondition(x < 10);
            return 1;
        }
        
        void usesPreconditionOf(int x) {
            precondition(
                    preconditionOf(noPrecondition()) && 
//                                ^ error: target of preconditionOf must have a single precondition
                    preconditionOf(twoPreconditions(x)));
//                                ^ error: target of preconditionOf must have a single precondition
        }
    }
}
