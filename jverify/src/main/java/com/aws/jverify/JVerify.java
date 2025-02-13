package com.aws.jverify;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
}

@Retention(RetentionPolicy.SOURCE)  // Keep annotation at runtime
@Target(ElementType.METHOD)            // Can only be used on classes/interfaces
@interface Pure {
}

@Retention(RetentionPolicy.SOURCE)  // Keep annotation at runtime
@Target(ElementType.METHOD)            // Can only be used on classes/interfaces
@interface Ghost {
}


@Retention(RetentionPolicy.SOURCE)  // Keep annotation at runtime
@Target(ElementType.TYPE_USE)            // Can only be used on classes/interfaces
@interface Unbounded {
}

@Retention(RetentionPolicy.SOURCE)  // Keep annotation at runtime
@Target(ElementType.TYPE_USE)            // Can only be used on classes/interfaces
@interface Nat {
}