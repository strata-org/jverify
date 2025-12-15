package com.aws.jverify.verifier.tests.verification.externalcontracts;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import com.aws.jverify.Unbounded;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 2)
public class LibraryContractGhostFieldErrors {

    @Contract(BigInteger.class)
    class DummyBigIntegerContract {

        @Unbounded
        int value;

        public byte byteValueExact() {
//                  ^ error: a contract for an impure method may only call super or this, or throw a ContractException in its body
            var x = 3;
            throw new ContractException();
        }
        
        @Pure
        int intValue() {
            reads(this);
            //noinspection ConstantValue
            precondition(value >= -10 && value <= 10);
            
            int createError;
//              ^ error: variable declaration of 'createError' must have an initializer because it is in a pure context
            return value;
        }

        @Pure
        DummyBigIntegerContract add(DummyBigIntegerContract delta) {
            postcondition((DummyBigIntegerContract b) -> b.value == this.value + delta.value);
            // try not specifying a pure body using the contract class
            throw new RuntimeException();
//          ^ error: a pure block must end in a return or if-else statement
        }
    }
    
    @Contract(RuntimeException.class)
    static class RuntimeExceptionContract {
        public RuntimeExceptionContract() {
        }
    }
}

