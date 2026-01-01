package com.aws.jverify.testengine;

import com.aws.jverify.verifier.Backend;

import java.lang.annotation.Annotation;

public record JVerifyTestRecord(String skip, boolean verifyByDefault, boolean useBuiltinContracts,
                                boolean continueOnErrors, int exitCode, int dafnyVerified, int dafnyErrors,
                                String[] additionalFiles, boolean verifyPrintedDafny,
                                int javaErrors, int javaVerified, int javaSkipped, Backend[] BACKENDS,
                                int[] performanceTicks) implements JVerifyTest {
    @Override
    public Class<? extends Annotation> annotationType() {
        return JVerifyTest.class;
    }
}
