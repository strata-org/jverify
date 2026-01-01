package com.aws.jverify.verifier.dafny;

import javax.tools.Diagnostic;
import java.net.URI;
import java.util.List;

public record VerificationResults(
        int verificationPassedMethods,
        int verificationFailedMethods,
        int verificationFailedAssertions,
        int verificationSkippedMethods,
        int performanceTicks) {
    
}