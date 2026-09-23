package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;

/**
 * Generics is NOT what blocks this, despite the fixture's location. The front end
 * translates it (T2 is a method type parameter, so it becomes a Laurel TVar in the
 * procedure's typeArgs) and it reaches Strata, producing real verification errors
 * rather than "Unsupported type: T2".
 *
 * <p>What remains is that {@code someStatic} is {@code @Pure} yet its body is not
 * visible to {@code user}, so {@code check(x == 3)} cannot be proved. That is the
 * CONSTRAINED-OUTPUT transparency gap, not a generics gap -- {@code someStatic}
 * returns a plain {@code int}, i.e. Laurel {@code int32}. Measured with four
 * {@code @Pure} statics each returning a literal:
 *
 * <ul>
 * <li>{@code <T> boolean genBool(T)} -- generic, unconstrained -- caller VERIFIES
 * <li>{@code <T> @Unbounded int genUnbounded(T)} -- generic, unconstrained -- VERIFIES
 * <li>{@code <T> int genBounded(T)} -- generic, constrained -- caller FAILS
 * <li>{@code int nonGenBounded()} -- NOT generic, constrained -- caller FAILS
 * </ul>
 *
 * A generic pure method is transparent; the non-generic control fails identically.
 * So the type parameter is irrelevant and the constrained return type is the cause.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_CONSTRAINED_OUTPUT_TRANSPARENCY}, exitCode = 4, methodsVerified = 4, errorCount = 1)
public class PolymorphicStaticsVerification {
    record R() {}
    static class Generic<T> {
        // TODO add when we support static fields
        //static int staticFieldsAreNeverGeneric = 3;
        
        @Pure
        public static <T2> int someStatic(T2 value, boolean valid) {
            precondition(valid);
//                       ^^^^^ Related location: this proposition could not be proved
            return 3;  
        }
    }
    
    void user() {
        var x = Generic.someStatic(new R(), true);
        check(x == 3);
        var y = Generic.someStatic(new R(), false);
//              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: function precondition could not be proved
        // TODO add when we support static fields
        // check(Generic.staticFieldsAreNeverGeneric == 3);
    }
}
