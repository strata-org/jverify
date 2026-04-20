package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 2)
public class PolymorphismResolutionErrors3 {
    
    public static void valueObjectHasNoEqualityBoth(Object a, Object b) {
        var c = a == b;
//                ^ error: '==' is only allowed when at least one operand's type is impure
    }
}