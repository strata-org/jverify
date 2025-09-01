package com.aws.jverify.builtin;

import com.aws.jverify.*;
import static com.aws.jverify.JVerify.*;

import java.math.BigInteger;
import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

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

@Contract(value = List.class, immutable = true)
abstract class ListContract<E> implements List<E> {

    JVerify.Sequence<E> elements;

    @Pure
    static <E> List<E> of() {
        postcondition((List<E> r) ->
                r.size() == 0);
        throw new ContractException();
    }

    @Pure
    static <E> List<E> of(E e1) {
        postcondition((List<E> r) ->
                r.size() == 1 &&
                        r.get(0) == e1);
        throw new ContractException();
    }

    @Pure
    static <E> List<E> of(E e1, E e2) {
        postcondition((List<E> r) ->
                r.size() == 2 &&
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
        precondition(0 <= index && index < size());
        postcondition((E e) -> e == elements.get(index));
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
    public boolean contains(Object o) {
        postcondition((boolean r) ->
                r == JVerify.exists((int i) ->
                        0 <= i && i < elements.size() && elements.get(i).equals(o)));
        throw new ContractException();
    }
}

@Contract(value = Set.class, immutable = true)
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


@Contract(value = Map.class, immutable = true)
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
                                        .map((K k) -> (java.util.Map.Entry<K,V>)new MapEntry<K, V>(k, elements.get(k))));
        throw new ContractException();
    }

    @Pure
    public static <K, V> Entry<K, V> entry(K key, V value) {
        postcondition((Entry<K, V> r) -> r.getKey() == key && r.getValue() == value);
        throw new ContractException();
    }
}

@Contract(immutable = true)
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

// It would be better to use Map.entry(key, value) directly,
// but that currently doesn't work because we end up with <K, V> type parameters
// on both Map and Map.entry, and while Java ignores the outer type parameters
// for static methods, Dafny gets confused and doesn't resolve.
record MapEntry<K, V>(K key, V value) implements Map.Entry<K, V> {
    @Override
    @Pure
    public K getKey() {
        return key;
    }

    @Override
    @Pure
    public V getValue() {
        return value;
    }

    @Override
    @Verify(false)
    public V setValue(V value) {
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

@Contract(value = Optional.class, immutable = true)
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

@Contract(value = String.class, immutable = true)
class StringContract extends StringTypeContract {

    @Pure
    public StringContract concat(StringContract str) {
        postcondition((StringContract r) -> jequals(r.chars, chars.concat(str.chars)));
        throw new ContractException();
    }

    @Pure
    public int indexOf(int ch) {
        postcondition((int result) -> -1 <= result && result < this.chars.size());
        postcondition((int result) -> result == -1
                ? JVerify.forall((int i) -> JVerify.implies(0 <= i && i < chars.size(), chars.get(i) != ch))
                : chars.get(result) == ch && JVerify.forall((int j) -> JVerify.implies(0 <= j && j < result, chars.get(j) != ch))
        );
        throw new ContractException();
    }

    @Pure
    public int length() {
        postcondition((int result) -> result == chars.size());
        throw new ContractException();
    }

    @Pure
    public char charAt(int index) {
        precondition(0 <= index && index < chars.size());
        return chars.get(index);
    }

    @Pure
    public boolean isEmpty() {
        return chars.size() == 0;
    }

    @Pure
    public StringContract substring(int beginIndex) {

        precondition(beginIndex >= 0 && beginIndex <= chars.size());
        postcondition((StringContract r) -> JVerify.jequals(r.chars, chars.drop(beginIndex)));
        throw new ContractException();
    }

    @Pure
    public String substring(int beginIndex, int endIndex) {
        precondition(beginIndex >= 0 && beginIndex <= endIndex && endIndex <= chars.size());
        postcondition((StringContract r) -> JVerify.jequals(r.chars, chars.subsequence(beginIndex, endIndex)));
        throw new ContractException();
    }

//    @Pure
//    public boolean equals(Object anObject) {
//        return anObject instanceof StringContract otherString &&
//                chars.equals(otherString.chars);
//    }

    @Pure
    public boolean startsWith(StringContract prefix) {
        return startsWith(prefix, 0);
    }

    @Pure
    public boolean startsWith(StringContract prefix, int toffset) {
        postcondition((boolean r) -> r == (toffset >= 0 &&
                prefix.chars.size() + toffset <= chars.size() &&
                jequals(
                        chars.drop(toffset).take(prefix.chars.size()),
                        prefix.chars)
        ));
        throw new ContractException();
    }
}