package com.aws.jverify.builtin;

import com.aws.jverify.Contract;
import java.lang.annotation.Native;
import java.util.Collection;

@Contract(Object.class)
class ObjectContract {    
}

@Contract(Short.class)
class ShortContract {
    public static final short MIN_VALUE = -32768;
    public static final short MAX_VALUE = 32767;
}

@Contract(Comparable.class)
class ComparableContract {

}

@Contract(Number.class)
class NumberContract {
    
}

@Contract(Collection.class)
class CollectionContract {

}

@Contract(Integer.class)
class IntegerContract extends NumberContract {
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int MIN_VALUE = 0x80000000;
}

@Contract(Long.class)
class LongContract extends NumberContract {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;
}