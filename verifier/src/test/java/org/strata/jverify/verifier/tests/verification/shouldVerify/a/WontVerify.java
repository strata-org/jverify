package org.strata.jverify.verifier.tests.verification.shouldVerify.a;

import org.strata.jverify.Verify;

import static org.strata.jverify.JVerify.check;

public class WontVerify {
    @Verify(true)
    void foo() {
        check(false);
    }
}
