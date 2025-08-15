package com.aws.jverify.verifier.tests.javasupport.records;

import com.aws.jverify.Modifiable;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.postcondition;

@JVerifyTest(exitCode = 22)
class RecordsDafnyResolutionError {
    record Foo() {}
    static void assignRecordToModifiableObject() {
        @Modifiable Object o = new Foo();
//      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ Error: RHS (of type Foo) not assignable to LHS (of type ModifiableObject)
    }
}