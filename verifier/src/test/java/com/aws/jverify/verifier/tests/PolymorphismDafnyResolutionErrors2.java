package com.aws.jverify.verifier.tests;

import com.aws.jverify.Immutable;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(dafnyVerified = 1, dafnyErrors = 0)
public class PolymorphismDafnyResolutionErrors2 {
    
    public static void valueObjectHasNoEquality() {
        // Annotations on method parameters are broken ATM, so these are locals
        @Immutable Object a = new Object();
        Object b = new Object();
        var c = a == b;
    }
}