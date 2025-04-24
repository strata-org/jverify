// TEST: exitCode=4 dafnyVerified=0 dafnyErrors=1

package com.aws.jverify.verifier.tests;

import static com.aws.jverify.JVerify.*;

class AssertFalse {
    static void Foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
}