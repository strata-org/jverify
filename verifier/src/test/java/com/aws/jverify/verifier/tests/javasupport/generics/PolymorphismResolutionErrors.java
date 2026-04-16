package com.aws.jverify.verifier.tests.javasupport.generics;

import com.aws.jverify.Impure;
import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 22)
public class PolymorphismResolutionErrors {
    
    public static <T> void modifiableObjectIsNotTop(T value) {
        @Impure Object o = value;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ImpureObject)
    }
}