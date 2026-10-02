package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;

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
