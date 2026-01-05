package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Impure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(methodsVerified = 2, errorCount = 0)
public class PolymorphismDafnyResolutionErrors2 {
    
    public static void valueObjectHasNoEquality() {
        // Annotations on method parameters are broken ATM, so these are locals
        Object a = new Object();
        @Impure Object b = new Object();
        var c = a == (@Impure Object)b;
    }
}
