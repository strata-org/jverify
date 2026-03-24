package com.aws.jverify.verifier.tests.examples;

import com.aws.jverify.Verify;
import com.aws.jverify.testengine.JVerifyTest;

import static com.aws.jverify.JVerify.*;
import static com.aws.jverify.verifier.Backend.Strata;

// @Verify(false) is handled by the pipeline's VerifyAnnotationCompiler.
// The annotated method should not be verified.
@JVerifyTest(exitCode = 0, BACKENDS = { Strata })
class StrataVerifyFalse {
    static void verified() {
        check(true);
    }

    @Verify(false)
    static void notVerified() {
        check(false); // Would fail if verified
    }
}
