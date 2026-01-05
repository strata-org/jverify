package com.aws.jverify.verifier.tests.javasupport.classes;

import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(methodsVerified = 2, errorCount = 0)
public class ConstructorsVerified {
    static class Box {
        private final int value;

        public Box(int value_) {
            this.value = value_;
            postcondition(this.value == value_);
        }
    }
}
