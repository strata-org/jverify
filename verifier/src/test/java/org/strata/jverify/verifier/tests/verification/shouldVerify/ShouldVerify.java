// ^ b/WillVerify.java(10:9-10:21) Error: assertion does not hold
package org.strata.jverify.verifier.tests.verification.shouldVerify;

import org.strata.jverify.Verify;
import org.strata.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.postcondition;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4,
        additionalFiles = {
        "./a/WontVerify.java", 
        "./a/package-info.java", 
        "./b/WillVerify.java", 
        "./b/package-info.java" },
        methodsVerified = 2, methodsSkipped = 7, errorCount = 6
)
public class ShouldVerify {

    @Verify(value = true, overrideChildren = true)
    static class VerifiedConstructor {

        @Verify(false)
        public VerifiedConstructor() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion does not hold
        }

        @Verify(false)
        void isVerified() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion does not hold
        }
    }

    @Verify(value = false, overrideChildren = true)
    static class ShouldVerify2 {
        @Verify(true)
        void notVerified() {
            check(false);
        }
    }

    @Verify(value = false, overrideChildren = false)
    static class ShouldVerify3 {
        @Verify(true)
        void isVerified() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion does not hold
        }

        void notVerifiedMethodCanUseCodeWithoutContracts() {
            BigInteger bigInteger = new BigInteger("1");
            check(false);
        }
    }

    @Verify(value = true, overrideChildren = false)
    static class ShouldVerify4 {
        @Verify(true)
        void foo5() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion does not hold
        }

        void foo6() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion does not hold
        }
    }

    @Verify(false)
    public ShouldVerify() {
        // test that verify false works for constructors 
        postcondition(false);
    }

}
