package org.strata.jverify.verifier.tests.javasupport.generics;

import org.strata.jverify.Impure;
import org.strata.jverify.testengine.JVerifyTest;
import org.strata.jverify.testengine.Blocker;

@JVerifyTest(expectedToFail = true, blockedBy = {Blocker.STRATA_GENERICS}, exitCode = 22)
public class PolymorphismResolutionErrors {
    
    public static <T> void modifiableObjectIsNotTop(T value) {
        @Impure Object o = value;
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type T) not assignable to LHS (of type ImpureObject)
    }
}