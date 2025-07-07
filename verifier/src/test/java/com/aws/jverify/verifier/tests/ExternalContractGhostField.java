package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 8, dafnyErrors = 0)
public class ExternalContractGhostField {
    static void test(BigIntegerContract v) {
        precondition(v.ghost == 1);
        var b = v.add(v);
        check(b.intValue() == 2);
    }
}

@Contract(BigInteger.class)
class BigIntegerContract {
    
    // Ghost field supposed to hold the semantics of BigInteger
    @Unbounded
    int ghost;
    
    @Pure
    int intValue() {
        reads(this);
        //noinspection ConstantValue
        precondition(ghost >= Integer.MIN_VALUE && ghost <= Integer.MAX_VALUE);
        postcondition((Integer r) -> r == ghost);
        throw new ContractException();
    }

    BigIntegerContract add(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.ghost == this.ghost + v.ghost);
        throw new ContractException();
    }
    
    /*
    Test static pure bodyless method
     */
    @Pure
    public static BigInteger valueOf(long val) {
        throw new ContractException();
    }
}