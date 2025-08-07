package com.aws.jverify.verifier.tests.classes;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 0, dafnyVerified = 5, dafnyErrors = 0)
public class ConstructorsVerified {
    class Box {
        private final int value;

        public Box(int value_) {
            this.value = value_;
            postcondition(this.value == value_);
        }
    }
}
