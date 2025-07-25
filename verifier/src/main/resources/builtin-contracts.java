package com.aws.jverify.builtin;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

import java.math.BigInteger;
import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;
import static com.aws.jverify.JVerify.check;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.Consumer;
import java.util.Collection;
import java.util.List;
import java.util.SequencedCollection;

@Contract(Comparable.class)
class ComparableContract<T> {
}

@Contract(value = Number.class, immutable = true)
class NumberContract {
    
}

@Contract(Collection.class)
interface CollectionContract<E> {

}

@Contract(SequencedCollection.class)
interface SequencedCollectionContract<E> extends Collection<E> { }

@Contract(Short.class)
class ShortContract {
    public static final short MIN_VALUE = -32768;
    public static final short MAX_VALUE = 32767;
}

@Contract(Integer.class)
class IntegerContract {
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int MIN_VALUE = 0x80000000;

    public static Integer valueOf(int i) {
        postcondition((Integer b) -> b.intValue() == i);
        throw new ContractException();
    }

    @Pure
    public int intValue() {
        throw new ContractException();
    }
}

@Contract(Double.class)
class DoubleContract {
}

@Contract(Long.class)
class LongContract {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;

    public static Long valueOf(long l) {
        postcondition((Long b) -> b.longValue() == l);
        throw new ContractException();
    }

    @Pure
    public long longValue() {
        throw new ContractException();
    }
}

@Contract(Boolean.class)
class BooleanContract {

    public static Boolean valueOf(boolean b) {
        postcondition((Boolean boxed) -> boxed.booleanValue() == b);
        throw new ContractException();
    }

    @Pure
    public boolean booleanValue() {
        throw new ContractException();
    }
}

@Contract
class IntFunction<R> implements java.util.function.IntFunction<R> {
    public R apply(int value) {
        throw new ContractException();
    }
}

@Contract
abstract class IntPredicateContract implements IntPredicate {
}

@Contract
abstract class PredicateContract<T> implements Predicate<T> {
}

class HelperForBigIntegerContract {
    @Erased
    @Pure
    static boolean isAllDigits(String v) {
        return forall((int i) -> !(0<=i && i<v.length()) || (v.charAt(i) >= '0' && v.charAt(i) <= '9'));
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

    @Erased
    @Pure
    static @Unbounded int pow(@Unbounded int x, @Nat int n) {
        decreases(n);
        return (n == 0 ? 1 : x * pow(x, n-1));
    }
}

@Contract(value = BigInteger.class, immutable = true)
class BigIntegerContract  {
    // Field holding the value of the BigInteger
    @Unbounded int intValue;

    BigIntegerContract(String val) {
        precondition(HelperForBigIntegerContract.isValidString(val));
        postcondition(intValue == HelperForBigIntegerContract.stringToInt(val));
        throw new ContractException();
    }

    @Pure
    int intValue() {
        precondition(intValue >= Integer.MIN_VALUE && intValue <= Integer.MAX_VALUE);
        postcondition((int b) -> b == intValue);
        throw new ContractException();
    }

    BigIntegerContract add(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue + v.intValue);
        throw new ContractException();
    }

    BigIntegerContract subtract(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue - v.intValue);
        throw new ContractException();
    }

    BigIntegerContract multiply(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue * v.intValue);
        throw new ContractException();
    }

    BigIntegerContract divide(BigIntegerContract v) {
        precondition(v.intValue != 0);
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue / v.intValue);
        throw new ContractException();
    }

    @Pure
    int compareTo(BigIntegerContract v) {
        postcondition((int r) -> (r < 0 && this.intValue < v.intValue) || (r == 0 && this.intValue == v.intValue) || (r > 0 && this.intValue > v.intValue));
        throw new ContractException();
    }

    BigIntegerContract abs() {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue < 0 ? -this.intValue : this.intValue));
        throw new ContractException();
    }

    BigIntegerContract min(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue < v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    BigIntegerContract max(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue > v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    BigIntegerContract mod(BigIntegerContract v) {
        precondition(v.intValue != 0);
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue % v.intValue);
        throw new ContractException();
    }

    BigIntegerContract negate() {
        postcondition((BigIntegerContract b) -> b.intValue == -this.intValue);
        throw new ContractException();
    }

    BigIntegerContract pow(int exponent) {
        precondition(exponent >= 0);
        postcondition((BigIntegerContract b) -> b.intValue == HelperForBigIntegerContract.pow(intValue, exponent));
        throw new ContractException();
    }

    static BigIntegerContract valueOf(long val) {
        postcondition((BigIntegerContract b) -> b.intValue == val);
        throw new ContractException();
    }

    @Pure
    public int signum() {
        postcondition((int b) -> b == (intValue < 0 ? -1 : intValue == 0 ? 0 : 1));
        throw new ContractException();
    }

}
