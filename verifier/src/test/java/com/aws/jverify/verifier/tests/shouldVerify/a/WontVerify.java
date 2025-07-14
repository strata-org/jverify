package com.aws.jverify.verifier.tests.shouldVerify.a;

import com.aws.jverify.Verify;

import static com.aws.jverify.JVerify.check;

public class WontVerify {
    @Verify(true)
    void foo() {
        check(false);
    }
}
