package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import com.aws.jverify.ContractException;

import java.lang.annotation.Native;
import java.util.Collection;
import java.util.List;

@Contract(Object.class)
class ObjectContract {    
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
class CollectionContract {

}

@Contract(List.class)
class ListContract<T> {
    public static List<T> of(T first, T second) {
        throw new ContractException();
    }
}

@Contract(Integer.class)
class IntegerContract implements NumberContract {
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int MIN_VALUE = 0x80000000;
}

@Contract(Long.class)
class LongContract implements NumberContract {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;
}