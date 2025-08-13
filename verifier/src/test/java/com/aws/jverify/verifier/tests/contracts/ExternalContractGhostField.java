package com.aws.jverify.verifier.tests.contracts;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 11, dafnyErrors = 0)
public class ExternalContractGhostField {
    static void test(DummyBigIntegerContract v) {
        precondition(v.value == 1);
        var b = v.add(v);
        check(b.intValue() == 2);
    }
}

@Contract(BigInteger.class)
class DummyBigIntegerContract {
    
    @Unbounded
    int value;
    
    @Pure
    int intValue() {
        reads(this);
        //noinspection ConstantValue
        precondition(value >= -10 && value <= 10);
        postcondition((int r) -> r == value);
        throw new ContractException();
    }

    DummyBigIntegerContract add(DummyBigIntegerContract delta) {
        postcondition((DummyBigIntegerContract b) -> b.value == this.value + delta.value);
        throw new ContractException();
    }


    // Test static pure bodyless method
    @Pure
    public static DummyBigIntegerContract valueOf(long val) {
        throw new ContractException();
    }
}
