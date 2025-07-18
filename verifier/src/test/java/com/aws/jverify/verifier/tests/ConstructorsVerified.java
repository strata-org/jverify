package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(dafnyVerified = 1, dafnyErrors = 0)
public class ConstructorsVerified {
    class Box {
        private final int value;

        public Box(int value_) {
            this.value = value_;
            postcondition(this.value == value_);
        }
    }
}