package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.Impure;
import org.strata.jverify.testengine.JVerifyTest;

@JVerifyTest(skip = "Strata: not yet supported", methodsVerified = 2, errorCount = 0)
public class PolymorphismResolutionErrors2 {
    
    public static void valueObjectHasNoEquality() {
        // Annotations on method parameters are broken ATM, so these are locals
        Object a = new Object();
        @Impure Object b = new Object();
        var c = a == (@Impure Object)b;
    }
}
