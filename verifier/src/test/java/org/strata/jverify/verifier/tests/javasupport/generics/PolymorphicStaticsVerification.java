package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.Pure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.check;
import static org.strata.jverify.JVerify.precondition;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 4, methodsVerified = 4, errorCount = 1)
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
