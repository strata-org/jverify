// ^ /builtin-contracts.java(71:36-71:50) Related location: this proposition could not be proved
package com.aws.jverify.verifier.tests;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.JVerify;
import com.aws.jverify.Nullable;
import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.postcondition;
import static com.aws.jverify.JVerify.precondition;

@JVerifyTest(exitCode = 4, dafnyVerified = 9, dafnyErrors = 3, useBuiltinContracts = true)
public class ListTest {

    void verifying() {
        var s = List.of("one", "two", "three");
        check(s.size() == 3);
        check(s.get(1).equals("two"));
        check(s.contains("two"));

        var s2 = List.of();
        check(s2.isEmpty());
    }

    void checkFails() {
        var s = List.of("one", "two");
        check(s.size() ==  0);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
    }

    void preconditionFails() {
        var s = List.of("one", "two");
        var x = s.get(2);
//              ^^^^^^^^ Error: function precondition could not be proved
    }
}

class SetTest {

    void verifying() {
        var s = Set.of("one", "two", "three");
        check(s.size() == 3);
        check(s.contains("two"));
        // TODO: Need a stronger contract on equals to prove this
//        check(!s.contains("four"));

        var s2 = Set.of();
        check(s2.isEmpty());
    }

    void checkFails() {
        var s = Set.of("one", "two");
        check(s.size() ==  0);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
    }
}

class MapTest {

    void verifying() {
        var s = Map.of("one", "two", "three", "four");
        check(s.size() == 2);
        check(s.containsKey("three"));

        var s2 = Map.of();
        check(s2.isEmpty());
    }

    void checkFails() {
        var s = Map.of("one", "two");
        check(s.size() ==  0);
//      ^^^^^^^^^^^^^^^^^^^^^ Error: assertion might not hold
    }
}

@Contract(value = Set.class, immutable = true)
abstract class SetContract<E> implements Set<E> {

    JVerify.Set<E> elements;

    static <E> Set<E> of() {
        postcondition((Set<E> r) ->
                r.size() == 0);
        throw new ContractException();
    }

    static <E> Set<E> of(E e1) {
        postcondition((Set<E> r) ->
                r.size() == 1 &&
                r.contains(e1));
        throw new ContractException();
    }

    static <E> Set<E> of(E e1, E e2) {
        postcondition((Set<E> r) ->
                r.size() == 2 &&
                r.contains(e1) &&
                r.contains(e2));
        throw new ContractException();
    }

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


//class HelperForMapContract {
//
//    static <K, V> boolean matchingKeyExists(JVerify.Map<Object, V> map, Object o) {
//        postcondition((boolean r) ->
//                r == JVerify.exists((K other) ->
//                        map.contains(other) && other.equals(o)));
//    }
//
//    static <K, V> K matchingKey(JVerify.Map<Object, V> map, Object o) {
//        precondition(matchingKeyExists(map, o));
//        postcondition((K other) ->
//                map.contains(other) && other.equals(o));
//    }
//}
//
//interface HelperForMap<K, V> {
//    boolean matchingKeyExists(JVerify.Map<Object, V> map, Object o);
//    K matchingKey(JVerify.Map<Object, V> map, Object o);
//}

@Contract(value = Map.class, immutable = true)
abstract class MapContract<K, V> implements Map<K, V> {

    JVerify.Map<Object, V> elements;

    static <K, V> Map<K, V> of() {
        postcondition((Map<K, V> r) ->
                r.size() == 0);
        throw new ContractException();
    }

    static <K, V> Map<K, V> of(K k1, V v1) {
        postcondition((Map<K, V> r) ->
                r.size() == 1 &&
                r.containsKey(k1) &&
                r.get(k1) == v1);
        throw new ContractException();
    }

    static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        postcondition((Map<K, V> r) ->
                r.size() == 2 &&
                r.containsKey(k1) &&
                r.get(k1) == v1 &&
                r.containsKey(k2) &&
                r.get(k2) == v2);
        throw new ContractException();
    }

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
    public boolean containsKey(Object o) {
        postcondition((boolean r) ->
                r == JVerify.exists((K other) ->
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

    @Override
    public Set<Entry<K, V>> entrySet() {
        postcondition((Set<Entry<K, V>> r) -> JVerify.<SetContract<K>>contractOf(r).elements == elements.entries());
        throw new ContractException();
    }
}
