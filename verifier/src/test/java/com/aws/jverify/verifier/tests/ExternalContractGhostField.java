package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.concurrent.atomic.AtomicInteger;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 6, dafnyErrors = 0)
public class ExternalContractGhostField {
    static void test(AtomicIntegerContract v) {
        precondition(v.value == 1);
        var b = v.addAndGet(v.get());
        check(b == 2);
    }
}

@Contract(AtomicInteger.class)
class AtomicIntegerContract {
    
    @Unbounded
    int value;
    
    @Pure
    int get() {
        reads(this);
        //noinspection ConstantValue
        precondition(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE);
        postcondition((Integer r) -> r == value);
        throw new ContractException();
    }

    int addAndGet(int delta) {
        postcondition((Integer b) -> b == this.value + delta);
        throw new ContractException();
    }

    /*
    Test static pure bodyless method
    TODO: re-add this test
    @Pure
    public static AtomicInteger valueOf(long val) {
        throw new ContractException();
    }
     */
}