package com.aws.jverify.verifier.tests.verification.abstractContracts;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(dafnyVerified = 9, dafnyErrors = 0)
public class AbstractContractsVerification {
    static class C {
        void foo() {
            precondition(isAbstract());
        }
    }
}
