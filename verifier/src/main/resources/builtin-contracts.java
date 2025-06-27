package com.aws.jverify.builtin;

import com.aws.jverify.Erased;
import com.aws.jverify.Contract;
import com.aws.jverify.Pure;
import com.aws.jverify.Nat;
import com.aws.jverify.Unbounded;
import static com.aws.jverify.JVerify.*;

import com.aws.jverify.ContractException;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.SequencedCollection;

@Contract(Object.class)
class ObjectContract {
    public ObjectContract() {} 
}

@Contract(Short.class)
class ShortContract {
    public static final short MIN_VALUE = -32768;
    public static final short MAX_VALUE = 32767;
}

@Contract(Comparable.class)
class ComparableContract<T> {

}

@Contract(Number.class)
interface NumberContract {
    
}

@Contract(Collection.class)
interface CollectionContract<E> {

}

@Contract(List.class)
abstract class ListContract<E> implements SequencedCollection<E> {
    public static <E> List<E> of(E first, E second) {
        throw new ContractException();
    }
}

@Contract(SequencedCollection.class)
interface SequencedCollectionContract<E> extends Collection<E> {
    
}

@Contract(Integer.class)
class IntegerContract {
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int MIN_VALUE = 0x80000000;
}

@Contract(Double.class)
class DoubleContract {
}

@Contract(Long.class)
class LongContract {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;
}
/*
class HelperForBigIntegerContract {

    @Erased
    @Pure
    static boolean isAllDigits(String v) {
        return forall((Integer i) -> !(0<=i && i<v.length()) || (v.charAt(i) >= '0' && v.charAt(i) <= '9'));
    }

    @Erased
    @Pure
    static boolean isValidString(String v) {
        return (v.length() == 0 ||
                ((v.charAt(0) == '+' || v.charAt(0) == '-') && isAllDigits(v.substring(1))) ||
                isAllDigits(v));
    }
    @Erased
    @Pure
    static @Unbounded int stringToInt(String v) {
        decreases(v.length());
        return
                v.length() == 0 ? 0 :
                        (v.charAt(0) == '+' ?
                        stringToInt(v.substring(1)) :
                        (v.charAt(0) == '-' ? -stringToInt(v.substring(1)) :
                                (v.charAt(v.length()-1)-'0') + 10*stringToInt(v.substring(0,v.length()-1))
                                ));
    }

    @Pure
    static @Unbounded int value(BigIntegerContract bc) {
        return bc.value;
    }
}

@Contract(BigInteger.class)
class BigIntegerContract {

    public @Unbounded int value;

    // Required for other constructors to be taken into account.
    public BigIntegerContract() {
    }

    public BigIntegerContract(String val) {
        precondition(HelperForBigIntegerContract.isValidString(val));
        postcondition(value == HelperForBigIntegerContract.stringToInt(val));
        throw new ContractException();
    }

    @Pure
    public int intValue() {
        reads(this);
        precondition(value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE);
        postcondition((Integer r) -> r == value);
        throw new ContractException();
    }

    @Pure
    public BigInteger add(BigInteger v) {
        reads(this);
        reads(v);
        postcondition((BigInteger b) -> HelperForBigIntegerContract.value(b) == HelperForBigIntegerContract.value(this) + HelperForBigIntegerContract.value(v));
        throw new ContractException();
    }

}

 */