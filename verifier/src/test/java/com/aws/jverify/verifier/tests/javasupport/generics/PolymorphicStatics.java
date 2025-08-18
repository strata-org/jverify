package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;

@JVerifyTest(dafnyVerified = 4, dafnyErrors = 0)
public class PolymorphicStatics {
    record R() {}
    static class Generic<T> {
        @Pure
        public static <T2> int someStatic(T2 value) {
          return 3;  
        }
    }
    
    void user() {
        var x = Generic.someStatic(new R());
        check(x == 3);
    }
}
