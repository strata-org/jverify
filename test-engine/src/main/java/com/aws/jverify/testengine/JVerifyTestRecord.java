package com.aws.jverify.testengine;

import com.aws.jverify.verifier.Backend;

import java.lang.annotation.Annotation;

public record JVerifyTestRecord(String skip, boolean verifyByDefault, boolean useBuiltinContracts,
                                boolean continueOnErrors, int exitCode, 
                                String[] additionalFiles, boolean verifyPrintedDafny,
                                int methodsInvalid, int failedAssertions, int methodsVerified, 
                                int methodsSkipped, Backend[] BACKENDS,
                                int[] performanceTicks) implements JVerifyTest {
    @Override
    public Class<? extends Annotation> annotationType() {
        return JVerifyTest.class;
    }
}
