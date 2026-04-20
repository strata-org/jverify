package org.strata.jverify.verifier.tests.verification.shouldVerify.b;

import org.strata.jverify.Verify;

import static org.strata.jverify.JVerify.check;

public class WillVerify {
    @Verify(false)
    void foo() {
        check(false);
    }
}
