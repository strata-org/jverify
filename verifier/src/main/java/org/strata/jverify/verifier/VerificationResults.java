package org.strata.jverify.verifier;

public record VerificationResults(
        int verificationPassedMethods,
        int verificationFailedMethods,
        int verificationFailedAssertions,
        int verificationSkippedMethods) {
    
}