package com.aws.jverify.verifier.dafny;

import com.aws.jverify.Nullable;

import javax.tools.Diagnostic;
import java.util.List;

public record JVerifyResults(
        List<Diagnostic<?>> diagnostics,
        int exitCode,
        @Nullable VerificationResults verificationResults) {}
