package com.aws.jverify;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class JVerify {

    public static boolean implies(boolean antecedant, boolean consequent) {
        return !antecedant || consequent;
    }
    
    public static void check(boolean condition) {
    }

    public static void precondition(boolean condition) {
    }

    public static void invariant(boolean condition) {
    }
    
    public static <T> void postcondition(Function<T, Boolean> predicate) {
    }

    public static <T> void postcondition(Supplier<Boolean> predicate) {
    }

    public static void postcondition(boolean predicate) {
    }

    /**
     * Specifies that the given heap object (and its fields) may be read in the current context.
     * This is only necessary within {@link Pure} methods, which otherwise cannot read the object or its fields.
     */
    public static void reads(Object object) {
    }

    public static <T> boolean forall(Function<T, Boolean> predicate) {
        throw new UnsupportedOperationException("Verification-only method called at runtime");
    }

    public static <T1, T2> boolean forall(BiFunction<T1, T2, Boolean> predicate) {
        throw new UnsupportedOperationException("Verification-only method called at runtime");
    }

    public static <T> Sequence<T> sequence(T[] array) {
        return new ArraySequence<>(array);
    }

    public static Sequence<Integer> sequence(int[] array) {
        return new ArraySequence<>(array);
    }

    // Uses Object instead of T[] in order to accommodate primitive arrays
    private record ArraySequence<T>(Object array) implements Sequence<T> {
    }
    
    public static boolean forall() {
        throw new RuntimeException();
    }
    
    public static <T> T old(T value) {
        throw new RuntimeException();
    }
}


