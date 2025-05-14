package com.aws.jverify.verifier.tests;

import com.aws.jverify.testengine.JVerifyTest;
import static com.aws.jverify.JVerify.*;

@JVerifyTest(exitCode = 0, dafnyVerified = 2, dafnyErrors = 0)
class Constructors {
    public int f;
    public int g = 22;
    public int h = 33;
    public Constructors() {
        postcondition(this.g==22); // Value of g is set by the default initializer
        postcondition(this.f==3);  // Value of f is set by the constructor
        postcondition(this.h==4);  // Value of h is set by the default initializer then changed by the constructor
        f = 3;
        h = 4;
    }

    public static void foo() {
        Constructors c = new Constructors();
        check(c.f==3);
        check(c.g==22);
    }
}