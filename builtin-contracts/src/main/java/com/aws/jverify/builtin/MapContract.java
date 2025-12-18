package com.aws.jverify.builtin;

import com.aws.jverify.*;
import java.util.Map;
import java.util.Set;
import static com.aws.jverify.JVerify.*;

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
        // TODO: postconditions needs something like :| to get a hold of the key
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