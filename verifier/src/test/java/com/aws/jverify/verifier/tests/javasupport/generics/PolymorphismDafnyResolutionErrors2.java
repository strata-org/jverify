package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Reference;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 22)
public class PolymorphismDafnyResolutionErrors2 {
    
    public static void valueObjectHasNoEquality() {
        // Annotations on method parameters are broken ATM, so these are locals
        Object a = new Object();
        @Reference Object b = new Object();
        var c = a == (@Reference Object)b;
//              ^ Error: == can only be applied to expressions of types that support equality (got Object)
    }
}