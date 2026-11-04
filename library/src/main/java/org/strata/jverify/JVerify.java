package org.strata.jverify;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class JVerify {

    public static boolean implies(boolean antecedent, boolean consequent) {
        return !antecedent || consequent;
    }

    /**
     * Assume the provided condition.
     */
    public static void assume(boolean condition) {
    }
    
    /**
     * Verifies that the given condition holds at this point in the program.
     * This is functionally equivalent to Java's {@code assert} statement for verification purposes.
     * <p>
     * Note: You can also use Java's native {@code assert} statement, which JVerify will verify
     * in the same way as {@code check()}. Both produce identical verification conditions.
     * 
     * @param condition the condition to verify
     */
    public static void check(boolean condition) {
    }

    public static void precondition(boolean condition) {
    }

    public static void precondition(BooleanSupplier condition) {
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
    public static void decreases(Object value) {}
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

    public static void postcondition(LongPredicate predicate) {
    }

    /**
     * Declare a postcondition for a void method (or any method whose return
     * value is not needed). The {@link BooleanSupplier} takes no arguments and
     * captures the enclosing scope directly, e.g.
     * {@code postcondition(() -> x >= 0)}.
     * <p>
     * Like the other JVerify contract primitives, this is a marker interpreted
     * by the verifier and has no effect at runtime.
     */
    public static void postcondition(BooleanSupplier predicate) {
    }

    /**
     * Return the precondition of the given expression
     * Currently only works when the expression is a method call
     * and only works for method calls that return a value.
     */
    public static <T> boolean preconditionOf(T value) {
        throw new ContractException();
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

    public static IntSequence range(int startInclusive, int endExclusive) {
        throw new VerificationMethodExecutedException();
    }

    public static interface BooleanPredicate {
        boolean test(boolean value);
    }

    public static interface IntBiPredicate {
        boolean test(int value1, int value2);
    }

    /**
     * Specifies that the given heap object (and its fields) may be read in the current context.
     * JVerify currently accepts this clause but does not use it.
     */
    public static void reads(Object object) {
    }

    /**
     * Specify that the given object(s) may be modified in this method.
     */
    public static void modifies(Object object) {
    }

    /**
     * Open an <em>exceptional behavior case</em>: when {@code condition} holds on
     * entry, this method <strong>must</strong> throw. The
     * condition may only read parameters, not the heap.
     * <p>
     * The case's clauses chain off the returned handle, so a case is one
     * statement and a clause cannot exist without its case:
     * {@code throwsOn(b == 0).postcondition((AppException e) -> ...).modifies(log);}
     * Stating any case claims the enumeration is complete: the verifier checks
     * that every throwing path satisfies some case's condition, so
     * {@code throwsOn(false)} states the method never throws, which is
     * different from stating no case at all (which leaves throwing paths
     * unconstrained).
     * <p>
     * Requires the method to declare its thrown type(s) in its {@code throws}
     * clause. Like every contract primitive, a no-op at runtime.
     */
    public static ThrowsOnCase throwsOn(boolean condition) {
        return ThrowsOnCase.INSTANCE;
    }

    /**
     * One exceptional behavior case, opened by {@link #throwsOn}. The clause
     * methods mirror the normal-exit vocabulary — {@code postcondition} and
     * {@code modifies} — scoped to this case's throwing path, exactly as
     * Laurel reuses {@code ensures}/{@code modifies} inside a
     * {@code throwsOn} block. Each returns the case so clauses chain; all are
     * no-ops at runtime.
     */
    public static final class ThrowsOnCase {
        private static final ThrowsOnCase INSTANCE = new ThrowsOnCase();

        private ThrowsOnCase() {
        }

        /**
         * Exceptional postcondition of this case: when the case's throw
         * happens, the property holds of the thrown value.
         * <p>
         * The lambda's parameter must be typed at the method's declared throws
         * type (the least common ancestor, for a multi-type {@code throws}
         * clause); narrow to a specific exception type inside the property
         * with {@code instanceof} and a cast, e.g.
         * {@code .postcondition((AppException e) -> implies(e instanceof ParseError, ((ParseError) e).code > 0))}.
         * Several {@code postcondition} clauses in one case conjoin.
         */
        public <E extends Throwable> ThrowsOnCase postcondition(Predicate<E> property) {
            return this;
        }

        /**
         * Exceptional frame of this case: on the throwing path it covers, only
         * the given object(s) may be modified. Same argument forms as
         * {@link JVerify#modifies},
         * including {@link JVerify#everything()}. A case with no stated frame
         * gets the same default as an unstated {@link JVerify#modifies}.
         */
        public ThrowsOnCase modifies(Object object) {
            return this;
        }
    }

    /**
     * Can be used in reads clauses to refer to all objects
     */
    public static Object everything() {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Can be used as the argument to a call to 'precondition' to make the precondition abstract
     * A subclass of the current class can implement that abstract clause by redefining it.
     */
    public static Object isAbstract() {
        throw new VerificationMethodExecutedException();
    }

    public static boolean isAbstractBoolean() {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Takes a fully applied method call and returns the reads clause of that call
     */
    public static Object readsOf(Object value) {
        throw new VerificationMethodExecutedException();
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
    public static long old(long value) {
        throw new ContractException();
    }
    public static boolean old(boolean value) {
        throw new ContractException();
    }

    /**
     * Provides observational equality. Two values are observationally equal if they can not be distinguished.
     * For primitive values, jequals behaves the same as `==`
     * For objects such as records and classes:
     * - If the type is impure, jequals behaves like `==`, reference equality
     * - If the type is pure, jequals behaves as structural equality, 
     *   recursively testing for observational equality on all its fields. 
     * For pure types, shallow structural equality implies observational equality, because:
     * - JVerify makes using `==` (reference equality) illegal
     * - pure types only have immutable fields
     * <p>
     * <b>Current limitation - the pure case is not yet implemented.</b> Nothing gets
     * structural {@code jequals} today. Every reference type lowers to an opaque Laurel
     * composite ({@code JavaToLaurelCompiler.translateType}), and {@code Eq} on a
     * composite is rewritten to reference equality, so records - which are pure -
     * compare by identity rather than by contents.
     * <p>
     * This is unsound in the negation direction, not merely incomplete: the verifier
     * proves {@code !jequals(p, q)} for two separately constructed records with equal
     * components, so a branch guarded on {@code jequals} is treated as dead and a false
     * assertion inside it is never checked. See {@code tests/javasupport/Jequals.java},
     * which pins the current behaviour so it cannot change unnoticed.
     * <p>
     * Records are expected to stop being composites once Laurel gains a value/record
     * ("Frozen") type. Until then there is no translatable structural equality for
     * records in verified code: a record's {@code equals()} has no translatable
     * structural meaning, so it fails loudly rather than offering a working
     * alternative. Do not rely on a
     * proved {@code !jequals(...)} over pure operands.
     */
    public static <T> boolean jequals(T left, T right) {
        throw new VerificationMethodExecutedException();
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

    public static boolean forall(LongPredicate predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static <T> boolean exists(Predicate<T> predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static boolean exists(IntPredicate predicate) {
        throw new VerificationMethodExecutedException();
    }

    public static boolean exists(LongPredicate predicate) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Create a Sequence with the given element(s)
     */
    public static <T> Sequence<T> elements(T element) {
        throw new VerificationMethodExecutedException();
    }

    /**
     * Cast a Java type to its contract type, to enable accessing fields of the contract class
     */
    public static <T, U> U cast(T element) {
        throw new VerificationMethodExecutedException();
    }
    
    /**
     * Cast a Java type to its contract type, to enable accessing fields of the contract class
     */
    public static <T, U> U cast(T element, Class<U> clazz) {
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

    public interface CharJSequence {
        /**
         * Returns the element at index {@code index}.
         */
        char get(int index);

        /**
         * Returns the subsequence starting at {@code fromIndex}, inclusive.
         */
        CharJSequence drop(int fromIndex);

        /**
         * Returns the subsequence ending at {@code toIndex}, inclusive.
         */
        CharJSequence take(int toIndex);

        /**
         * Returns the subsequence starting at {@code fromIndex}, inclusive,
         * and ending at {@code toIndex}, exclusive.
         */
        CharJSequence subsequence(int fromIndex, int toIndex);

        /**
         * Returns {@code true} if this sequence contains the specified element.
         */
        boolean contains(char element);

        /**
         * Returns the number of elements in this sequence.
         */
        @Unbounded int size();

        /**
         * Returns the number of elements in this sequence.
         */
        CharJSequence concat(CharJSequence next);
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
        boolean contains(T element);

        /**
         * Returns the number of elements in this sequence.
         */
        @Unbounded int size();
        
        /**
         * Returns the number of elements in this sequence.
         */
        Sequence<T> concat(Sequence<T> next);
    }

    public interface IntSequence {
        /**
         * Returns the element at index {@code index}.
         */
        int get(int index);
        
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
        
        /**
         * Returns the number of elements in this sequence.
         */
        @Unbounded int size();
    }

    public interface Set<T> {
        /**
         * Returns the set of all {@code T} values that satisfy the given predicate.
         */
        static <T> Set<T> all(Predicate<T> filter) {
            throw new ContractException();
        }

        /**
         * Returns {@code true} if this set contains the specified element.
         */
        boolean contains(T element);

        /**
         * Returns the number of elements in this sequence.
         */
        @Unbounded int size();

        /**
         * Returns the set found by applying the given mapping to every element.
         */
        <R> Set<R> map(Function<T, R> mapping);
    }

    /**
     * A map from {@code int} to {@code int}: the primitive specialization of {@link Map},
     * as {@link IntSequence} is of {@link Sequence}. Java generics admit only reference
     * types, so {@code Map<Integer, Integer>} yields boxed values that {@code ==} cannot
     * compare and whose {@code intValue()} is opaque; keeping keys and values primitive
     * lets ordinary integer operators apply.
     *
     * <p>This map is TOTAL: every key has a value, so there is no {@code contains}. A key
     * outside the range you care about holds an unconstrained value -- constrain the range
     * your contract uses.
     *
     * <p>Verification-only, like the rest of this class: it has no implementation.
     */
    public interface IntMap {
        /**
         * Returns the value mapped to the given {@code key}. This map is TOTAL: every key has a
         * value, so there is no {@code contains} and no absence to guard against.
         *
         * <p>Both the key and the result are {@link Unbounded}, matching the Laurel total map this
         * lowers to, whose values are the unconstrained integer. A plain {@code int} binding would
         * carry an {@code int32} range it cannot discharge, so bind results to
         * {@code @Unbounded int} and state any range you rely on -- for code units, say
         * {@code 0 <= m.get(i) && m.get(i) <= 65535} -- as a contract.
         */
        @Unbounded int get(@Unbounded int key);
    }

    public interface Map<K, V> {
        /**
         * Returns {@code true} if this map contains the specified key.
         */
        boolean contains(K element);

        /**
         * Returns the value mapped to the given {@code key}. This read is TOTAL: a key that is
         * not in the map yields an unconstrained value rather than an error, so guard with
         * {@link #contains} wherever absence matters.
         */
        V get(K key);

        /**
         * Returns the number of key/value pairs in this map.
         */
        @Unbounded int size();
    }

    public static class VerificationMethodExecutedException extends UnsupportedOperationException {
        public VerificationMethodExecutedException() {
            super("Verification-only method called at runtime");
        }
    }
}


