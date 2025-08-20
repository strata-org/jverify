package com.aws.jverify.verifier.tests.verification.externalcontracts;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(dafnyVerified = 10, dafnyErrors = 0)
public class LibraryContractGhostFieldVerification {
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

    // specifies a pure body using the contract class
    @Pure
    int intValue() {
        reads(this);
        //noinspection ConstantValue
        precondition(value >= -10 && value <= 10);
        return value;
    }

    // does not specify a pure body
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
