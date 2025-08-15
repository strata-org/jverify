package com.aws.jverify;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class JVerify {

    public static boolean implies(boolean antecedent, boolean consequent) {
        return !antecedent || consequent;
    }
    
    public static void check(boolean condition) {
    }

    public static void precondition(boolean condition) {
    }

    /**
     * Tell JVerify which value decreases on each
     * - iteration of the loop, when used in a loop
     * - recursive call, when used on a recursive method
     * Knowing which value decreases is required for termination proofs
     * <p>
     * When JVerify sees a recursive call, it compares the decreases call of the caller
     * with that of the callee, and will emit an error if the former is not greater than the latter.
     * <p>
     * Multiple values can be passed to the decreases call. 
     * If the caller and callee have decreases calls with different lengths,
     * then JVerify implicitly pads the shortest with a 'top' value, 
     * until they are the same length.
     * <p>
     * The top value is considered larger than any other value.
     * After padding, JVerify compares both lists of decreases values 
     * in the same way as two tuples would be compared, 
     * also known as a lexicographical comparison.
     */
    public static void decreases(Object... values) {}
    public static void decreases(int value) {}
    public static void decreases(int value1, int value2) {}

    public static void invariant(boolean condition) {
    }
    
    public static <T> void postcondition(Predicate<T> predicate) {
    }

    /**
     * The given expression must evaluate to true after this method call
     */
    public static void postcondition(boolean predicate) {
    }

    public static void postcondition(BooleanPredicate predicate) {
    }

    public static void postcondition(IntPredicate predicate) {
    }

    /** 
     * Takes the precondition of the given expression
     * and applies that to the current method 
     */
    public static <T> void copyPrecondition(T value) {
    }

    public static <T> Optional<T> callIfAble(Supplier<T> supplier) {
        throw new ContractException();
    }

    public static boolean callIfAble(Runnable action) {
        throw new ContractException();
    }

    public static interface BooleanPredicate {
        boolean test(boolean value);
    }

    public static interface IntBiPredicate {
        boolean test(int value1, int value2);
    }

    /**
     * Specifies that the given heap object(s) (and its fields) may be read in the current context.
     * This is only necessary within {@link Pure} methods, which otherwise cannot read the object or its fields.
     */
    public static void reads(Object object) { }

    /**
     * Can be used as an argument in reads and modifies clauses, 
     * to indicate that all objects can be read or modified
     */
    public static Object all() { 
        throw new ContractException();
    }

    /**
     * Specify that the given object(s) may be modified in this method.
     */
    public static void modifies(Object object) {
    }


    /**
     * Evaluates the given value using the program state with which the current method was called with.
     * This method can only be used in an erased context.
     */
    public static <T> T old(T value) {
        throw new ContractException();
    }
    public static int old(int value) {
        throw new ContractException();
    }

    /**
     * Returns true if the given object were allocated during the current method call.
     */
    public static <T> boolean fresh(Object object) {
        throw new ContractException();
    }
    
    public static <T> boolean forall(Function<T, Boolean> predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static <T1, T2> boolean forall(BiPredicate<T1, T2> predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static boolean forall(IntPredicate predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static boolean forall(IntBiPredicate predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static <T> boolean exists(Function<T, Boolean> predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static boolean exists(IntPredicate predicate) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Returns a {@link Sequence} representing the contents of the specified array.
     */
    public static <T> Sequence<T> sequence(T[] array) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Returns a {@link Sequence} representing the contents of the portion of the specified array
     * starting at {@code fromIndex}, inclusive.
     */
    public static <T> Sequence<T> sequence(T[] array, int fromIndex) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Returns a {@link Sequence} representing the contents of the portion of the specified array
     * starting at {@code fromIndex}, inclusive, and ending at {@code toIndex}, exclusive.
     */
    public static <T> Sequence<T> sequence(T[] array, int fromIndex, int toIndex) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Same as {@link #sequence(Object[])}, but for a primitive {@code int} array.
     */
    public static IntSequence sequence(int[] array) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Same as {@link #sequence(Object[], int)}, but for a primitive {@code int} array.
     */
    public static IntSequence sequence(int[] array, int fromIndex) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Same as {@link #sequence(Object[], int, int)}, but for a primitive {@code int} array.
     */
    public static IntSequence sequence(int[] array, int fromIndex, int toIndex) {
        throw new VerificationMethodExecutedException();
    }

    public interface Sequence<T> {
        /**
         * Returns the element at index {@code index}.
         */
        T get(int index);

        /**
         * Returns the subsequence starting at {@code fromIndex}, inclusive.
         */
        Sequence<T> drop(int fromIndex);

        /**
         * Returns the subsequence ending at {@code toIndex}, inclusive.
         */
        Sequence<T> take(int toIndex);

        /**
         * Returns the subsequence starting at {@code fromIndex}, inclusive,
         * and ending at {@code toIndex}, exclusive.
         */
        Sequence<T> subsequence(int fromIndex, int toIndex);

        /**
         * Returns {@code true} if this sequence contains the specified element.
         */
        boolean contains(Object element);

        /**
         * Returns the number of elements in this sequence.
         */
        @Unbounded int size();
    }

    public interface IntSequence {
        /**
         * Returns the subsequence starting at {@code fromIndex}, inclusive.
         */
        IntSequence drop(int fromIndex);

        /**
         * Returns the subsequence ending at {@code toIndex}, inclusive.
         */
        IntSequence take(int toIndex);

        /**
         * Returns the subsequence starting at {@code fromIndex}, inclusive,
         * and ending at {@code toIndex}, exclusive.
         */
        IntSequence subsequence(int fromIndex, int toIndex);

        /**
         * Returns {@code true} if this sequence contains the specified element.
         */
        boolean contains(int element);
    }

    public static class VerificationMethodExecutedException extends UnsupportedOperationException {
        public VerificationMethodExecutedException() {
            super("Verification-only method called at runtime");
        }
    }
}


