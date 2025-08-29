// ^ b/WillVerify.java(10:9-10:21) Error: assertion might not hold
package com.aws.jverify.verifier.tests.verification.shouldVerify;

import com.aws.jverify.Contract;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(exitCode = 4, dafnyVerified = 11, dafnyErrors = 6,
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
//          ^^^^^^^^^^^^ Error: assertion might not hold
        }

        @Verify(false)
        void isVerified() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion might not hold
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
//          ^^^^^^^^^^^^ Error: assertion might not hold
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
//          ^^^^^^^^^^^^ Error: assertion might not hold
        }

        void foo6() {
            check(false);
//          ^^^^^^^^^^^^ Error: assertion might not hold
        }
    }

    @Verify(false)
    static class VerifyFalseAffectsFields {
        static final BigInteger b1 = BigInteger.ZERO;
        static BigInteger b2 = BigInteger.ONE;
        BigInteger b3 = BigInteger.TWO;
    }

    @Contract(value = BigInteger.class, immutable = true)
    static abstract class BigIntegerContract {}
    @Contract(value = Number.class, immutable = true)
    static abstract class NumberContract {}
}
