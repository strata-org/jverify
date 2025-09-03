package com.aws.jverify.verifier.tests.verification.shouldVerify;

import com.aws.jverify.Contract;
import com.aws.jverify.Pure;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;
import com.aws.jverify.testing.LibraryClassA;
import com.aws.jverify.testing.LibraryClassB;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 13, dafnyErrors = 1)
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
    
    class OverridesEquals {
        final int a = 3; // TODO move assignment to constructor once we improve final verification support

        @Override
        public boolean equals(Object obj) {
            return obj instanceof OverridesEquals // instanceof check is not how it should be done but that's irrelevant to this test 
                    && a == ((OverridesEquals) obj).a;
        }
    }
    
    @Contract(value = LibraryClassA.class, immutable = true)
    class LibraryClassAContract {
        // assumed equals method is added
    }

    void testBodylessEquals(LibraryClassAContract first, LibraryClassAContract second) {
        // equals method was not given a body, so we can not prove much
        check(first.equals(second));
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
    }

    @Contract(value = LibraryClassB.class, immutable = true)
    static class LibraryClassBContract {
        @Override
        public boolean equals(Object obj) {
            return true;
        }
    }

    void testCustomEquals(LibraryClassBContract first, LibraryClassBContract second) {
        check(first.equals(second));
    }
    
    record ARecordThatImplementsAnInterface() implements I {

        @Override
        public int pure() {
            return 3;
        }
    }
    
    interface DiamondTop {
        @Verify(false)
        default int notVerifiedImpure() {
            return 3;
        }
    }

    interface LeftWithOverride extends DiamondTop {
        @Override
        default int notVerifiedImpure() {
            return 4;
        }
    }

    interface RightWithoutOverride extends DiamondTop {
    }
    
    static class DiamondBottom implements LeftWithOverride, RightWithoutOverride {}
}
