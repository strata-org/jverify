package com.aws.jverify.verifier.tests.shouldVerify.b;

import com.aws.jverify.Verify;

import static com.aws.jverify.JVerify.check;

public class WillVerify {
    @Verify(false)
    void foo() {
        check(false);
    }
}
