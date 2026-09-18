package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;

/**
 * STRATA_GENERICS is now the accurate blocker, for a different reason than it
 * used to be. The front end translates this (T2 is a method type parameter, so
 * it becomes a Laurel TVar in the procedure's typeArgs) and it REACHES Strata:
 * the run no longer refuses with "Unsupported type: T2" but produces real
 * verification errors. What is left is that {@code someStatic} is
 * {@code @Pure} yet its generic procedure does not behave transparently, so
 * {@code check(x == 3)} on line 27 -- which needs the body's {@code return 3}
 * to be visible -- cannot be proved. The expected diagnostics above describe
 * the transparent outcome.
 */
@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_GENERICS}, exitCode = 4, methodsVerified = 4, errorCount = 1)
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
