package com.aws.jverify.builtin;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

import java.math.BigInteger;
import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;

import java.util.*;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Contract(Comparable.class)
class ComparableContract<T> {
}

@Contract(value = Number.class, pure = true)
class NumberContract {

}

@Contract(BiFunction.class)
abstract class BiFunctionContract<T, U, R> implements BiFunction<T, U, R> {
    @Pure
    public R apply(T t, U u) {
        throw new ContractException();
    }
}

class SequenceHelper {
    @Pure
    public static <T> T reduce(Sequence<T> sequence, T identity, BinaryOperator<T> accumulator) {
        if (sequence.size() == 0) {
            return identity;
        }
        var sizeMin = sequence.size() - 1;
        return accumulator.apply(reduce(sequence.subsequence(0, sizeMin), identity, accumulator), sequence.get(sizeMin));
        // return reduce(sequence.drop(1), accumulator.apply(identity, sequence.get(0)), accumulator);
    }
}

@Contract(value = Stream.class, pure = true)
abstract class StreamContract<T> implements Stream<T> {

    public JVerify.Sequence<T> elements;

    @Pure
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return SequenceHelper.reduce(elements, identity, accumulator);
    }
}

@Contract(Collection.class)
abstract class CollectionContract<E> implements Collection<E> {

    @Override
    @Pure
    public int size() {
        reads(everything());
        throw new ContractException();
    }
}

@Contract(SequencedCollection.class)
interface SequencedCollectionContract<E> extends Collection<E> { }

abstract class ImmutableList<E> implements List<E> {
    @Override
    @Pure
    @Verify(false)
    public int size() {
        throw new ContractException();
    }
}

@Contract(value = List.class, pure = true)
abstract class ListContract<E> implements List<E> {
    
    @Pure
    static <E> ImmutableList<E> of() {
        postcondition((List<E> r) -> r instanceof ImmutableList<E> il && ((ImmutableList<E>)r).size() == 0);
        throw new ContractException();
    }

    @Pure
    static <E> List<E> of(E e1) {
        postcondition((List<E> r) -> r.size() == 1 && r.get(0) == e1);
        throw new ContractException();
    }

    @Pure
    static <E> List<E> of(E e1, E e2) {
        postcondition((List<E> r) -> r.size() == 2 &&
                        r.get(0) == e1 &&
                        r.get(1) == e2);
        throw new ContractException();
    }

    @Pure
    static <E> List<E> of(E e1, E e2, E e3) {
        postcondition((List<E> r) ->
                r.size() == 3 &&
                        r.get(0) == e1 &&
                        r.get(1) == e2 &&
                        r.get(2) == e3);
        throw new ContractException();
    }

    @Override
    @Pure
    public E get(int index) {
        reads(everything());
        precondition(0 <= index && index < size());
        throw new ContractException();
    }

    @Override
    @Pure
    public int size() {
        reads(everything());
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean isEmpty() {
        reads(everything());
        postcondition((boolean r) -> r == (size() == 0));
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean contains(Object o) {
        reads(everything());
        postcondition((boolean r) ->
                r == JVerify.exists((int i) ->
                        0 <= i && i < size() && get(i).equals(o)));
        throw new ContractException();
    }
}

@Contract(ArrayList.class)
abstract class ArrayListContract<E> extends ArrayList<E>{

    protected JVerify.Sequence<E> elements;
    
    public ArrayListContract() {
        postcondition(elements.size() == 0);
        throw new ContractException();
    }

    @Pure
    public Stream<E> stream() {
        reads(this);
        postcondition((Stream<E> s) -> cast(s, StreamContract.class).elements.size() == size());
        throw new ContractException();
    }
    
    @Override
    @Pure
    public E get(int index) {
        reads(this);
        decreases(0);
        precondition(0 <= index && index < size());
        postcondition((E e) -> e == elements.get(index));
        throw new ContractException();
    }

    @Override
    @Pure
    public int size() {
        reads(this);
        decreases(0);
        postcondition((int s) -> s == elements.size());
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean isEmpty() {
        reads(this);
        postcondition((boolean r) -> r == (elements.size() == 0));
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean contains(Object o) {
        reads(this);
        postcondition((boolean r) ->
                r == JVerify.exists((int i) ->
                        0 <= i && i < elements.size() && elements.get(i).equals(o)));
        throw new ContractException();
    }
    
    @Override
    public boolean add(E element) {
        modifies(this);
        postcondition((boolean r) -> elements == old(elements.concat(JVerify.elements(element))));
        throw new ContractException();
    }
} 

@Contract(value = Set.class, pure = true)
abstract class SetContract<E> implements Set<E> {

    JVerify.Set<E> elements;

    @Pure
    static <E> Set<E> of() {
        postcondition((Set<E> r) ->
                r.size() == 0);
        throw new ContractException();
    }

    @Pure
    static <E> Set<E> of(E e1) {
        postcondition((Set<E> r) ->
                r.size() == 1 &&
                        r.contains(e1));
        throw new ContractException();
    }

    @Pure
    static <E> Set<E> of(E e1, E e2) {
        postcondition((Set<E> r) ->
                r.size() == 2 &&
                        r.contains(e1) &&
                        r.contains(e2));
        throw new ContractException();
    }

    @Pure
    static <E> Set<E> of(E e1, E e2, E e3) {
        postcondition((Set<E> r) ->
                r.size() == 3 &&
                        r.contains(e1) &&
                        r.contains(e2) &&
                        r.contains(e3));
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean contains(Object o) {
        postcondition((boolean r) ->
                r == JVerify.exists((E other) ->
                        elements.contains(other) && other.equals(o)));
        throw new ContractException();
    }

    @Override
    @Pure
    public int size() {
        postcondition((int s) -> s == elements.size());
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean isEmpty() {
        postcondition((boolean r) -> r == (elements.size() == 0));
        throw new ContractException();
    }
}


@Contract(value = Map.class, pure = true)
abstract class MapContract<K, V> implements Map<K, V> {

    JVerify.Map<Object, V> elements;

    @Pure
    static <K, V> Map<K, V> of() {
        postcondition((Map<K, V> r) ->
                r.size() == 0);
        throw new ContractException();
    }

    @Pure
    static <K, V> Map<K, V> of(K k1, V v1) {
        postcondition((Map<K, V> r) ->
                r.size() == 1 &&
                        r.containsKey(k1) &&
                        r.get(k1) == v1);
        throw new ContractException();
    }

    @Pure
    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        postcondition((Map<K, V> r) ->
                r.size() == 2 &&
                        r.containsKey(k1) &&
                        r.get(k1) == v1 &&
                        r.containsKey(k2) &&
                        r.get(k2) == v2);
        throw new ContractException();
    }

    @Pure
    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        postcondition((Map<K, V> r) ->
                r.size() == 2 &&
                        r.containsKey(k1) &&
                        r.get(k1) == v1 &&
                        r.containsKey(k2) &&
                        r.get(k2) == v2 &&
                        r.containsKey(k3) &&
                        r.get(k3) == v3);
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean containsKey(Object key) {
        postcondition((boolean r) ->
                r == JVerify.exists((K other) ->
                        elements.contains(other) && other.equals(key)));
        throw new ContractException();
    }

    @Override
    @Pure
    public V get(Object key) {
        precondition(containsKey(key));
        // TODO: postcondition needs something like :| to get a hold of the key
        // that actually exists in the map according to containsKey
        throw new ContractException();
    }

    @Override
    @Pure
    public int size() {
        postcondition((int s) -> s == elements.size());
        throw new ContractException();
    }

    @Override
    @Pure
    public boolean isEmpty() {
        postcondition((boolean r) -> r == (elements.size() == 0));
        throw new ContractException();
    }

    @Override
    @Pure
    public Set<Entry<K, V>> entrySet() {
        postcondition((SetContract<Entry<K, V>> r) ->
                size() == r.size() &&
                        r.elements ==
                                JVerify.Set.all((K k) -> elements.contains(k))
                                        .map((K k) -> Map.entry(k, elements.get(k))));
        throw new ContractException();
    }

    @Pure
    public static <K, V> Entry<K, V> entry(K key, V value) {
        postcondition((Entry<K, V> r) -> r.getKey() == key && r.getValue() == value);
        throw new ContractException();
    }
}

@Contract(pure = true)
abstract class MapEntryContract<K, V> implements Map.Entry<K, V> {
    @Override
    @Pure
    public K getKey() {
        throw new ContractException();
    }

    @Override
    @Pure
    public V getValue() {
        throw new ContractException();
    }
}

@Contract(Short.class)
class ShortContract {
    public static final short MIN_VALUE = -32768;
    public static final short MAX_VALUE = 32767;
}

@Contract(Integer.class)
class IntegerContract {
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int MIN_VALUE = 0x80000000;

    @Pure
    public static int sum(int a, int b) {
        return a + b;
    }
    
    @Pure
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

    @Pure
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

    @Pure
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
    @Pure
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
        if (v.length() == 0) {
            return 0;
        }

        if (v.charAt(0) == '+') {
            return stringToInt(v.substring(1));
        }

        if (v.charAt(0) == '-') {
            return -stringToInt(v.substring(1));
        }

        return v.charAt(v.length()-1)-'0' + 
                10*stringToInt(v.substring(0,v.length()-1));
    }

    @Erased
    @Pure
    static @Unbounded int pow(@Unbounded int x, @Nat int n) {
        decreases(n);
        return (n == 0 ? 1 : x * pow(x, n-1));
    }
}

@Contract(value = BigInteger.class, pure = true)
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

    @Pure
    BigIntegerContract add(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue + v.intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract subtract(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue - v.intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract multiply(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue * v.intValue);
        throw new ContractException();
    }

    @Pure
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

    @Pure
    BigIntegerContract abs() {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue < 0 ? -this.intValue : this.intValue));
        throw new ContractException();
    }

    @Pure
    BigIntegerContract min(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue < v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    @Pure
    BigIntegerContract max(BigIntegerContract v) {
        postcondition((BigIntegerContract b) -> b.intValue == (this.intValue > v.intValue ? this.intValue : v.intValue));
        throw new ContractException();
    }

    @Pure
    BigIntegerContract mod(BigIntegerContract v) {
        precondition(v.intValue != 0);
        postcondition((BigIntegerContract b) -> b.intValue == this.intValue % v.intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract negate() {
        postcondition((BigIntegerContract b) -> b.intValue == -this.intValue);
        throw new ContractException();
    }

    @Pure
    BigIntegerContract pow(int exponent) {
        precondition(exponent >= 0);
        postcondition((BigIntegerContract b) -> b.intValue == HelperForBigIntegerContract.pow(intValue, exponent));
        throw new ContractException();
    }

    @Pure
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

@Contract(value = Optional.class, pure = true)
class OptionalContract<T> {

    private T value;
    private boolean isSet;

    //TODO: add precondition for null
    @Pure
    static <T> OptionalContract<T> of(T value) {
        postcondition((OptionalContract<T> o) -> o.value == value && o.isSet);
        throw new ContractException();
    }

    @Pure
    static <T> OptionalContract<T> empty() {
        postcondition((OptionalContract<T> o) -> !o.isSet);
        throw new ContractException();
    }

    //Currently the same as of because we are not able to handle null cases
    //TODO: add null check
    /*
    @Pure
    static <T> OptionalContract<T> ofNullable(T value) {
        postcondition((OptionalContract<T> o) ->  o.isSet && o.value == value);
        throw new ContractException();
    }
    */

    @Pure
    boolean isEmpty() {
        postcondition((boolean r) -> r == !isSet);
        throw new ContractException();
    }

    @Pure
    boolean isPresent() {
        postcondition((boolean r) -> r == isSet);
        throw new ContractException();
    }

    @Pure
    T orElse(T value) {
        postcondition((T b) -> isPresent()? b == this.value : b == value);
        throw new ContractException();
    }

    @Pure
    T get() {
        precondition(isSet);
        postcondition((T b) -> b == this.value);
        throw new ContractException();
    }
}