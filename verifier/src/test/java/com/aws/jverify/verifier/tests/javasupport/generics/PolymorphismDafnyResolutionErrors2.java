package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Pure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 3, dafnyErrors = 0)
public class PolymorphismDafnyResolutionErrors2 {
    
    public static void valueObjectHasNoEquality() {
        // Annotations on method parameters are broken ATM, so these are locals
        @Pure Object a = new Object();
        Object b = new Object();
        var c = a == (Object)b;
    }
}
