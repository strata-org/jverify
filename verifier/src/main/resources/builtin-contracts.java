package com.aws.jverify.builtin;

import com.aws.jverify.*;

import java.math.BigInteger;
import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;

import java.util.Collection;
import java.util.List;
import java.util.SequencedCollection;

import static com.aws.jverify.JVerify.*;

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

@Contract(String.class)
class StringContract {
    @Pure
    public static int indexOf(String s, int c) {
        postcondition((Integer result) -> result == -1 || (0 <= result && result < s.length() && s.charAt(result) == c));
        postcondition((Integer result) -> implies(result == -1, forall((Integer j) -> implies(0<=j&&j<s.length(),s.charAt(j)!=c))));
        postcondition((Integer result) -> implies(result >= 0, forall((Integer j) -> implies(0<=j&&j<result,s.charAt(j)!=c))));
        throw new ContractException();
    }
    @Pure
    public static boolean startsWith(String s, String prefix) {
        postcondition((Boolean b) -> implies(prefix.length() > s.length(),b==false));
        postcondition((Boolean b) -> implies(prefix.length() <= s.length() && forall((Integer i) -> implies(0<=i && i<prefix.length(), prefix.charAt(i) == s.charAt(i))), b==true));
        postcondition((Boolean b) -> implies(prefix.length() <= s.length() && exists((Integer i) -> 0<=i && i<prefix.length() && prefix.charAt(i) != s.charAt(i)), b==false));
        throw  new ContractException();
    }
}

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

    @Erased
    @Pure
    static @Unbounded int pow(@Unbounded int x, @Nat int n) {
        decreases(n);
        return (n == 0 ? 1 : x * pow(x, n-1));
    }
}

@Contract(BigInteger.class)
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
        reads(this);
        precondition(intValue >= Integer.MIN_VALUE && intValue <= Integer.MAX_VALUE);
        postcondition((Integer b) -> b == intValue);
        throw new ContractException();
    }

    BigIntegerContract add(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == this.intValue + v.intValue);
        throw new ContractException();
    }

    BigIntegerContract subtract(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == this.intValue - v.intValue);
        throw new ContractException();
    }

    BigIntegerContract multiply(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == this.intValue * v.intValue);
        throw new ContractException();
    }

    BigIntegerContract divide(BigIntegerContract v) {
        precondition(v.intValue != 0);
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == this.intValue / v.intValue);
        throw new ContractException();
    }

    @Pure
    int compareTo(BigIntegerContract v) {
        reads(this);
        reads(v);
        postcondition((Integer r) -> (r < 0 && this.intValue < v.intValue) || (r == 0 && this.intValue == v.intValue) || (r > 0 && this.intValue > v.intValue));
        throw new ContractException();
    }

    BigIntegerContract abs() {
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == (this.intValue < 0 ? -this.intValue : this.intValue));
        throw new ContractException();
    }

    BigIntegerContract min(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == (this.intValue < v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    BigIntegerContract max(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == (this.intValue > v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    BigIntegerContract mod(BigIntegerContract v) {
        precondition(v.intValue != 0);
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == this.intValue % v.intValue);
        throw new ContractException();
    }

    BigIntegerContract negate() {
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == -this.intValue);
        throw new ContractException();
    }

    BigIntegerContract pow(int exponent) {
        precondition(exponent >= 0);
        postcondition((BigIntegerContract b) -> fresh(b) && b.intValue == HelperForBigIntegerContract.pow(intValue, exponent));
        throw new ContractException();
    }

    static BigIntegerContract valueOf(long val) {
        postcondition((BigIntegerContract b) -> b.intValue == val);
        throw new ContractException();
    }

    @Pure
    public int signum() {
        reads(this);
        postcondition((Integer b) -> b == (intValue < 0 ? -1 : intValue == 0 ? 0 : 1));
        throw new ContractException();
    }

}
