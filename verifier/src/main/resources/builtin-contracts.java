package com.aws.jverify;

import java.lang.annotation.Native;

@Contract(Short.class)
class ShortContract {    /**
 * A constant holding the minimum value a {@code short} can
 * have, -2<sup>15</sup>.
 */
    public static final short   MIN_VALUE = -32768;
    public static final short   MAX_VALUE = 32767;
}

@Contract(Integer.class)
class IntegerContract {
    public static final int MAX_VALUE = 0x7fffffff;
    public static final int MIN_VALUE = 0x80000000;
}

@Contract(Long.class)
class LongContract {
    public static final long MIN_VALUE = 0x8000000000000000L;
    public static final long MAX_VALUE = 0x7fffffffffffffffL;
}
/**
 * A constant holding the minimum value a {@code long} can
 * have, -2<sup>63</sup>.
 */
