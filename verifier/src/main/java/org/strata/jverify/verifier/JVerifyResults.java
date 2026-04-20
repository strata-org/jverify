package org.strata.jverify.verifier;

import org.strata.jverify.Nullable;

import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.List;

public record JVerifyResults(
        List<Diagnostic<?>> diagnostics,
        int exitCode,
        @Nullable VerificationResults verificationResults) {}
