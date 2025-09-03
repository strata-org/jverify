package com.aws.jverify.verifier.tests.verification.shouldVerify;

import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 13, dafnyErrors = 0)
public class InheritedUnverifiedMembers {
    static class SoVirtual {
        @Pure
        @Verify(false)        
        int notVerified () {
            return 3;
        }
    }
    
    static class SoConcreteWithoutOverride extends SoVirtual {
        
    }
    
    static class SoConcreteWithOverride extends SoVirtual {
        @Override
        int notVerified() {
            return 4;
        }
    }
}
