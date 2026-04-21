package org.strata.jverify.verifier.tests;

import static org.strata.jverify.JVerify.check;

class AssertFalse {
    static void Foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion does not hold
    }
}