package com.aws.jverify.verifier.tests.verification.shouldVerify;

import com.aws.jverify.Contract;
import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

@JVerifyTest(dafnyVerified = 3, dafnyErrors = 0)
public class VerifyFalseAffectsFields {
    @Verify(false)
    static class AssumedFields {
        static final BigInteger b1 = BigInteger.ZERO;
        static BigInteger b2 = BigInteger.ONE;
        BigInteger b3 = BigInteger.TWO;
    }
    
    @Contract(value = BigInteger.class, immutable = true)
    static abstract class BigIntegerContract {}
    @Contract(value = Number.class, immutable = true)
    static abstract class NumberContract {}
}
