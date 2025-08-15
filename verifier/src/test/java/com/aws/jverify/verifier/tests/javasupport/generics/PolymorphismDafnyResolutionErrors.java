package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Reference;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 22)
public class PolymorphismDafnyResolutionErrors {
    
    public static <T> void referenceObjectIsNotTop(T value) {
        @Reference Object o = value;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ReferenceObject)
    }
}