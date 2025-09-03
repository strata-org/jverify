// ^ b/WillVerify.java(10:9-10:21) Error: assertion could not be proved
package com.aws.jverify.verifier.tests.verification.shouldVerify;

import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 19, dafnyErrors = 6,
        additionalFiles = {
        "./a/WontVerify.java", 
        "./a/package-info.java", 
        "./b/WillVerify.java", 
        "./b/package-info.java" }
)
public class ShouldVerify {

    @Verify(value = true, overrideChildren = true)
    static class VerifiedConstructor {

        @Verify(false)
        public VerifiedConstructor() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion could not be proved
        }

        @Verify(false)
        void isVerified() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion could not be proved
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
//          ^^^^^^^^^^^^ Error: assertion could not be proved
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
//          ^^^^^^^^^^^^ Error: assertion could not be proved
        }

        void foo6() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion could not be proved
        }
    }

}
