package com.aws.jverify.verifier.tests;

import com.aws.jverify.Immutable;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 22)
public class PolymorphismDafnyResolutionErrors2 {
    
    public static void valueObjectHasNoEquality() {
        // Annotations on method parameters are broken ATM, so these are locals
        @Immutable Object a = new Object();
        Object b = new Object();
        var c = a == b;
//              ^ Error: == can only be applied to expressions of types that support equality (got Object)
    }
}