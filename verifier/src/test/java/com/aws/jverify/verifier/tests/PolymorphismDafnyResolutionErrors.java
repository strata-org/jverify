package com.aws.jverify.verifier.tests;

import com.aws.jverify.Modifiable;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 22)
public class PolymorphismDafnyResolutionErrors {
    
    public static <T> void modifiableObjectIsNotTop(T value) {
        @Modifiable Object o = value;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ModifiableObject)
    }
}