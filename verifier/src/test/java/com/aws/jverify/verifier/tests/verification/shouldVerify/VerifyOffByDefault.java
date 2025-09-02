package com.aws.jverify.verifier.tests.verification.shouldVerify;

import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.check;
import static com.aws.jverify.JVerify.precondition;

@JVerifyTest(verifyByDefault = false, exitCode = 4, dafnyVerified = 15, dafnyErrors = 1)
public class VerifyOffByDefault {}

@Verify
class WithAttributeTrue {
    void foo() {
        check(false);
//      ^^^^^^^^^^^^ Error: assertion might not hold
    }
}

@Verify(value = false)
class WithAttributeFalse {
    void foo() {
        check(false);   // Not verified
    }
}

class NoAttribute {
    void foo() {
        check(false);   // Not verified
    }
}

// Showing that code we can't translate is fine if it doesn't have @Verify.
// (Using an invalid library method call rather than something we might
// handle later for test stability)
// Some cases like interfaces without contracts still cause errors
// at the time of writing this.
class CantTranslate {
    int pureWithMultipleStatements() {
        var x = 42;
        precondition(x == 3);   // Illegal use, not caught
        return 3;
    }
}
