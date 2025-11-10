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
    }
}
