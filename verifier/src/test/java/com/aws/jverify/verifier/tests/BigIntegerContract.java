package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, dafnyVerified = 10, dafnyErrors = 5)
@Contract(BigInteger.class)
class BigIntegerContract {
    
    // Ghost field supposed to hold the semantics of BigInteger
    @Unbounded
    int ghost;
    
    BigIntegerContract() {}
    BigIntegerContract(String val) {
        postcondition(ghost == val.length()); // In reality something more complex than length but this is for illustration purpose
        throw new ContractException();
    }
    @Pure
    int intValue() {
        reads(this);
        precondition(ghost >= Integer.MIN_VALUE && ghost <= Integer.MAX_VALUE);
        postcondition((Integer r) -> r == ghost);
        throw new ContractException();
    }
    
    @Pure
    BigIntegerContract add(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.ghost == this.ghost + v.ghost);
        throw new ContractException();
    }
}