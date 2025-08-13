package com.aws.jverify.verifier.tests.contracts;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0)
public class LibraryContractGhostFieldErrors {

    @Contract(BigInteger.class)
    class DummyBigIntegerContract {

        @Unbounded
        int value;

        @Pure
        int intValue() {
//          ^ error: pure method should have only one statement
            reads(this);
            //noinspection ConstantValue
            precondition(value >= -10 && value <= 10);
            
            var createError = 3;
            return value;
        }

        @Pure
        DummyBigIntegerContract add(DummyBigIntegerContract delta) {
//                              ^ error: pure method statement should be a return
            postcondition((DummyBigIntegerContract b) -> b.value == this.value + delta.value);
            // try not specifying a pure body using the contract class
            throw new RuntimeException();
        }
    }
}

