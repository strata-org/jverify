package com.aws.jverify.verifier.tests.verification.externalcontracts;

import com.aws.jverify.*;
import com.aws.jverify.testengine.JVerifyTest;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Stream;

import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 4, javaVerified = 6, javaErrors = 1)
public class LibraryContractGhostFieldVerification {
    static void test(BigInteger v) {
        precondition(DummyBigIntegerContract.convert(v).value == 1);
        BigInteger b = v.add(v);
        check(b.intValue() == 2);
    }
    
    void useFromStream(Stream<Integer> integerStream) {
        StreamContract<Integer> streamContract = StreamContract.fromStream(integerStream);
    }
    
    <T> T useAbsAndGet(List<T> list) {
        @SuppressWarnings("DataFlowIssue") 
        var x = list.get(-3);
//                       ^^ Error: value does not satisfy the subset constraints of 'nat31'
        return list.get(Math.abs(-3));
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

    @Pure
    @Erased
    public static DummyBigIntegerContract convert(BigInteger bi) {
        return JVerify.<BigInteger, DummyBigIntegerContract>cast(bi);
    }

    // does not specify a pure body
    BigInteger add(DummyBigIntegerContract delta) {
        postcondition((DummyBigIntegerContract b) -> b.value == this.value + delta.value);
        throw new ContractException();
    }

    // Test static pure bodyless method
    @Pure
    public static DummyBigIntegerContract valueOf(long val) {
        throw new ContractException();
    }
}

@Contract(value = Stream.class, pure = true)
abstract class StreamContract<T> implements Stream<T> {
    @Erased
    @Pure
    public static <T> StreamContract<T> fromStream(Stream<T> s) {
        return JVerify.<Stream<T>, StreamContract<T>>cast(s);
    }
}

@Contract
abstract class ListContract<E> implements List<E> {
    @Override
    public E get(@Nat int index) {
        throw new ContractException();
    }
}

@Contract(Math.class)
class MathContract {
    public static @Nat int abs(int x) {
        throw new ContractException();
    }
}