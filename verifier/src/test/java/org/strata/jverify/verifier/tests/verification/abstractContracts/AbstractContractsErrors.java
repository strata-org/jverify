package org.strata.jverify.verifier.tests.verification.abstractContracts;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 2)
public class AbstractContractsErrors {
    static class C {
        void foo() {
            postcondition((boolean)isAbstract());
//                                 ^ error: isAbstract() in clauses other than a precondition is not supported
            decreases(isAbstract());
//                    ^ error: isAbstract() in clauses other than a precondition is not supported
            reads(isAbstract());
//                ^ error: isAbstract() in clauses other than a precondition is not supported
            modifies(isAbstract());
//                   ^ error: isAbstract() in clauses other than a precondition is not supported
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
                    preconditionOf(twoPreconditions(x)));
        }
    }
}
