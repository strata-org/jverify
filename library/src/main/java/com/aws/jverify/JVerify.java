package com.aws.jverify;

import java.util.function.BiFunction;
import java.util.function.Function;

public class JVerify {
    public static void check(boolean condition) {
    }

    public static void precondition(boolean condition) {
    }

    public static void invariant(boolean condition) {
    }
    
    public static <T> void postcondition(Function<T, Boolean> predicate) {
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
        throw new VerificationMethodExecutedException();
    }

    public static <T1, T2> boolean forall(BiFunction<T1, T2, Boolean> predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static <T> Sequence<T> sequence(T[] array) {
        throw new VerificationMethodExecutedException();
    }

    public static Sequence<Integer> sequence(int[] array) {
        throw new VerificationMethodExecutedException();
    }

    public interface Sequence<T> {
        /**
         * Returns {@code true} if this sequence contains the specified element.
         */
        default boolean contains(T element) {
            throw new VerificationMethodExecutedException();
        }

        /**
         * Returns {@code true} if the portion of this sequence starting at {@code fromIndex}, inclusive,
         * contains the specified element.
         */
        default boolean contains(T element, int fromIndex) {
            throw new VerificationMethodExecutedException();
        }

        /**
         * Returns {@code true} if the portion of this sequence starting at {@code fromIndex}, inclusive,
         * and ending at {@code toIndex}, exclusive, contains the specified element.
         */
        default boolean contains(T element, int fromIndex, int toIndex) {
            throw new VerificationMethodExecutedException();
        }
    }

    public static class VerificationMethodExecutedException extends UnsupportedOperationException {
        public VerificationMethodExecutedException() {
            super("Verification-only method called at runtime");
        }
    }
}


