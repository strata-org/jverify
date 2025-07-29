package com.aws.jverify.verifier.tests.generics;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
public class PolymorphismDafnyResolutionErrors3 {
    
    public static void valueObjectHasNoEqualityBoth(Object a, Object b) {
        var c = a == b;
//                ^ error: '==' is only allowed when at least one operand's type is @Modifiable
    }
}