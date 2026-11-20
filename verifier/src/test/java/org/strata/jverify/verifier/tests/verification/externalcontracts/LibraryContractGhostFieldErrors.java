package org.strata.jverify.verifier.tests.verification.externalcontracts;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Transparent;
import org.strata.jverify.Unbounded;
import org.strata.jverify.testengine.Blocker;
import org.strata.jverify.testengine.JVerifyTest;

import java.math.BigInteger;

import static org.strata.jverify.JVerify.*;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.FRONTEND_DIAGNOSTIC}, exitCode = 2)
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
        
        @Transparent
        int intValue() {
            reads(this);
            //noinspection ConstantValue
            precondition(value >= -10 && value <= 10);
            return value;
        }

        DummyBigIntegerContract add(DummyBigIntegerContract delta) {
//                              ^ error: a contract for an impure method may only call super or this, or throw a ContractException in its body
            postcondition((DummyBigIntegerContract b) -> b.value == this.value + delta.value);
            throw new RuntimeException();
        }
    }
    
    @Contract(RuntimeException.class)
    static class RuntimeExceptionContract {
        public RuntimeExceptionContract() {
        }
    }
}

