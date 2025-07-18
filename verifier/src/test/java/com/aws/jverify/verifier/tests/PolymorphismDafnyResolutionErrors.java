package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 22)
public class PolymorphismDafnyResolutionErrors {
    public static <T> void refObjectIsNotTop(T value) {
        Object o = value;
//      ^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type Object)
    }
}