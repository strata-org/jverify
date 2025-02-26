package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Function;
import java.util.function.Supplier;

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


