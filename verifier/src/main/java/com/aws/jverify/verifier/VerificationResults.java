package com.aws.jverify.verifier;

import com.aws.jverify.Nullable;

public record VerificationResults(
        int verificationPassedMethods,
        int verificationFailedMethods,
        int verificationFailedAssertions,
        int verificationSkippedMethods,
        @Nullable Integer performanceTicks) {
    
}