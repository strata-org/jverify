package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;

@JVerifyTest(exitCode = 2)
public class PolymorphismDafnyResolutionErrors3 {
    
    public static void valueObjectHasNoEqualityBoth(Object a, Object b) {
        var c = a == b;
//                ^ error: '==' is not allowed when both operand's types could be String, a record, or a boxed primitive, unless one of the operands is a null literal
    }
}