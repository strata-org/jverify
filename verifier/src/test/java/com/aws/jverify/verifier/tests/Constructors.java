// TEST: exitCode=0 dafnyVerified=2 dafnyErrors=0

package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;

@JVerifyTest
class Constructors {
    public int f;
    public int g = 22;
    public Constructors() {
        postcondition(this.g==22);
        postcondition(this.f==3);
        f = 3;
    }

    public static void foo() {
        Constructors c = new Constructors();
        check(c.f==3);
        check(c.g==22); // Need the postcondition which currently makes the test fail
    }
}