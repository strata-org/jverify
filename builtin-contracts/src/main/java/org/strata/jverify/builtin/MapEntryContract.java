package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Pure;
import java.util.Map;

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