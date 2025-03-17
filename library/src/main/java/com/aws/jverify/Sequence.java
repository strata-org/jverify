package com.aws.jverify;

public interface Sequence<T> {

    public static <E> Sequence<E> empty() {
        throw new RuntimeException();
    }
    
    public static <E> Sequence<E> single(E value) {
        throw new RuntimeException();
    }


    default Sequence<T> concat(Sequence<T> other) {
        throw new RuntimeException();
    }

    default int size() {
        throw new RuntimeException();
    }

    default T get(int index) {
        throw new RuntimeException();
    }
    
    default Sequence<T> subsequence(int from, int to) {
        throw new UnsupportedOperationException("Verification-only method called at runtime");
    }

    default Sequence<T> drop(int amount) {
        throw new UnsupportedOperationException("Verification-only method called at runtime");
    }

    default Sequence<T> take(int amount) {
        throw new UnsupportedOperationException("Verification-only method called at runtime");
    }

    /**
     * Returns {@code true} if this sequence contains the specified element.
     */
    default boolean contains(T element) {
        throw new UnsupportedOperationException("Verification-only method called at runtime");
    }

    /**
     * Returns {@code true} if the portion of this sequence starting at {@code fromIndex}, inclusive,
     * contains the specified element.
     */
    default boolean contains(T element, int fromIndex) {
        throw new UnsupportedOperationException("Verification-only method called at runtime");
    }

    /**
     * Returns {@code true} if the portion of this sequence starting at {@code fromIndex}, inclusive,
     * and ending at {@code toIndex}, exclusive, contains the specified element.
     */
    default boolean contains(T element, int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("Verification-only method called at runtime");
    }
}
