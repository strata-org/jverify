package org.strata.jverify.builtin;

import org.strata.jverify.Contract;
import org.strata.jverify.ContractException;
import org.strata.jverify.Transparent;
import java.util.Map;

@Contract(pure = true)
abstract class MapEntryContract<K, V> implements Map.Entry<K, V> {
    @Override
    @Transparent
    public K getKey() {
        throw new ContractException();
    }

    @Override
    @Transparent
    public V getValue() {
        throw new ContractException();
    }
}