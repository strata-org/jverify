package com.aws.jverify.verifier.tests.verification.shouldVerify;

import com.aws.jverify.Contract;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 13, dafnyErrors = 0)
public class InheritedUnverifiedMembers {
    static class ThroughBaseClass {
        @Pure
        @Verify(false)        
        int notVerifiedPure() {
            return 3;
        }

        @Verify(false)
        int notVerifiedImpure() {
            return 3;
        }
    }
    
    static class ThroughBaseClassWithoutOverride extends ThroughBaseClass {
        
    }
    
    static class ThroughBaseClassWithOverride extends ThroughBaseClass {
        @Override
        int notVerifiedPure() {
            return 4;
        }

        @Override
        int notVerifiedImpure() {
            return 4;
        }
    }

    static abstract class ThroughInterface implements I {}
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
