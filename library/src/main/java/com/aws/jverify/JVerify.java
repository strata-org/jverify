package com.aws.jverify;

import java.util.function.BiFunction;
import java.util.function.Function;
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
     * 
     * When JVerify sees a recursive call, it compares the decreases call of the caller
     * with that of the callee, and will emit an error if the former is not greater than the latter.
     * 
     * Multiple values can be passed to the decreases call. 
     * If the caller and callee have decreases calls with different lengths,
     * then JVerify implicitly pads the shortest with a 'top' value, 
     * until they are the same length.
     * 
     * The top value is considered larger than any other value.
     * After padding, JVerify compares both lists of decreases values 
     * in the same way as two tuples would be compared, 
     * also known as a lexicographical comparison.
     */
    public static void decreases(Object... values) {
    }
    
    public static void invariant(boolean condition) {
    }
    
    public static <T> void postcondition(Function<T, Boolean> predicate) {
    }

    /**
     * The given function must evaluate to true after this method call
     */
    public static <T> void postcondition(Supplier<Boolean> predicate) {
    }

    /**
     * The given expression must evaluate to true after this method call
     */
    public static void postcondition(boolean predicate) {
    }

    /**
     * Specifies that the given heap object(s) (and its fields) may be read in the current context.
     * This is only necessary within {@link Pure} methods, which otherwise cannot read the object or its fields.
     */
    public static void reads(Object... objects) {
    }

    /**
     * Specify that the given object(s) may be modified in this method.
     */
    public static void modifies(Object... objects) {
    }

    /**
     * Evaluates the given value using the program state with which the current method was called with.
     * This method can only be used in an erased context.
     */
    public static <T> T old(T value) {
        throw new ContractException();
    }

    /**
     * Returns true if the given objects(s) were allocated during the current method call.
     */
    public static <T> T fresh(Object... objects) {
        throw new ContractException();
    }

    public static <T> boolean forall(Function<T, Boolean> predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static <T1, T2> boolean forall(BiFunction<T1, T2, Boolean> predicate) {
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
    public static Sequence<Integer> sequence(int[] array) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Same as {@link #sequence(Object[], int)}, but for a primitive {@code int} array.
     */
    public static Sequence<Integer> sequence(int[] array, int fromIndex) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Same as {@link #sequence(Object[], int, int)}, but for a primitive {@code int} array.
     */
    public static Sequence<Integer> sequence(int[] array, int fromIndex, int toIndex) {
        throw new VerificationMethodExecutedException();
    }

    public interface Sequence<T> {
        /**
         * Returns the subsequence starting at {@code fromIndex}, inclusive.
         */
        default Sequence<T> drop(int fromIndex) {
            throw new VerificationMethodExecutedException();
        }

        /**
         * Returns the subsequence ending at {@code toIndex}, inclusive.
         */
        default Sequence<T> take(int toIndex) {
            throw new VerificationMethodExecutedException();
        }

        /**
         * Returns the subsequence starting at {@code fromIndex}, inclusive,
         * and ending at {@code toIndex}, exclusive.
         */
        default Sequence<T> subsequence(int fromIndex, int toIndex) {
            throw new VerificationMethodExecutedException();
        }

        /**
         * Returns {@code true} if this sequence contains the specified element.
         */
        default boolean contains(T element) {
            throw new VerificationMethodExecutedException();
        }
    }

    public static class VerificationMethodExecutedException extends UnsupportedOperationException {
        public VerificationMethodExecutedException() {
            super("Verification-only method called at runtime");
        }
    }
}


