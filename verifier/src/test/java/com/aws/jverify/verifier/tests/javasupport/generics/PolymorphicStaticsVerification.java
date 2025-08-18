package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.precondition;

@JVerifyTest(dafnyVerified = 7, dafnyErrors = 0)
public class PolymorphicStaticsVerification {
    record R() {}
    static class Generic<T> {
        
        @Pure
        public static <T2> int someStatic(T2 value, boolean valid) {
            precondition(valid);
//          ^ Related location: this proposition could not be proved
            return 3;  
        }
    }
    
    void user() {
        var x = Generic.someStatic(new R(), true);
        check(x == 3);
        var y = Generic.someStatic(new R(), false);
//              ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: function precondition could not be proved
    }
}
