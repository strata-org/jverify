package com.aws.jverify;

import java.util.function.Function;

public class JVerify {
    public static void check(boolean condition) {
    }

    public static <T> void requires(boolean condition) {
    }

    public static void invariant(boolean condition) {
    }
    
    public static <T> void ensures(Function<T, Boolean> predicate) {
    }

    public static void ensures(Boolean predicate) {
    }
}


