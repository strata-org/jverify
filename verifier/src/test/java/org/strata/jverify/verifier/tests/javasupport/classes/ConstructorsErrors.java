package org.strata.jverify.verifier.tests.javasupport.classes;

import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 2)
public class ConstructorsErrors {
    class Box {
        private int value;

        public Box(int value_) {
            this.value = value_;
            postcondition(this.value == value_);
//                       ^ error: for constructors, calls to JVerify contract methods must come either immediately after the call to 'super' or 'this', or at the end of the body
            var b = 3;
        }
    }
}