package com.aws.jverify.builtin;

import com.aws.jverify.Contract;

@Contract(Short.class)
class ShortContract {
    public static final short MIN_VALUE = -32768;
    public static final short MAX_VALUE = 32767;
}