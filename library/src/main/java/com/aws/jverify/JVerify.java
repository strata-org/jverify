package com.aws.jverify;

import java.util.function.Function;

public class JVerify {
    public static void check(boolean condition) {
    }

    public static <T> void precondition(boolean condition) {
    }

    public static void invariant(boolean condition) {
    }
    
    public static <T> void postcondition(Function<T, Boolean> predicate) {
    }

    public static void postcondition(Boolean predicate) {
    }
}


