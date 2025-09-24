package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Pure;
import com.aws.jverify.PureRef;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 22)
public class PolymorphismDafnyResolutionErrors {
    
    public static <@PureRef T> void modifiableObjectIsNotTop(T value) {
        Object o = value;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ModifiableObject)
    }
}