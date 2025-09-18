package com.aws.jverify.verifier.tests;

import static com.aws.jverify.JVerify.check;

class AssertFalse {
    static void Foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion could not be proved
    }
}