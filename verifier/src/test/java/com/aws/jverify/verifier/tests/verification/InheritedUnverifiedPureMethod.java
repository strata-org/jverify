package com.aws.jverify.verifier.tests.verification;

import com.aws.jverify.Contract;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

/*
This is testing a feature that we don't have yet, so the test currently expects a bad result
 */
@JVerifyTest(exitCode = 22)
public class InheritedUnverifiedPureMethod {

    // Through base class
    static class Extender extends Base {}
//               ^^^^^^^^ Error: class 'Constructable?Extender' does not implement trait function 'Base.pure'
    
    static class Base {
        @Verify(false)
        @Pure
        public int pure() {
            return 0;
        }
    }

    // Thread interface and abtract class
    static abstract class D implements I {}
//                        ^ Error: class 'Constructable?D' does not implement trait function 'I.pure'
    interface I {
        int pure();

        @Contract
        class IContract implements I {
            @Pure
            @Override
            public int pure() {
                return 0;
            }
        }
    }
}
