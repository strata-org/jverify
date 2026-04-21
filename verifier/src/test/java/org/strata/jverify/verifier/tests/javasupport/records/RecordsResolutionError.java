package org.strata.jverify.verifier.tests.javasupport.records;

import org.strata.jverify.Impure;
import org.strata.jverify.testengine.JVerifyTest;

import static org.strata.jverify.JVerify.postcondition;

@JVerifyTest(skip = "Strata: not yet supported", exitCode = 22)
class RecordsResolutionError {
    record Foo() {}
    static void assignRecordToModifiableObject() {
        @Impure Object o = new Foo();
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type Foo) not assignable to LHS (of type ImpureObject)
    }
}