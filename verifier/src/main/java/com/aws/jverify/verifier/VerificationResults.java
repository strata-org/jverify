package com.aws.jverify.verifier;

public record VerificationResults(
        int verificationPassedMethods,
        int verificationFailedMethods,
        int verificationFailedAssertions,
        int verificationSkippedMethods) {
    
}