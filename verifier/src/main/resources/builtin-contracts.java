package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;
import com.aws.jverify.Pure;

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