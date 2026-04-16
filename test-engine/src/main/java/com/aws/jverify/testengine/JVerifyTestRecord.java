package com.aws.jverify.testengine;

import java.lang.annotation.Annotation;

public record JVerifyTestRecord(String skip, boolean verifyByDefault, boolean useBuiltinContracts,
                                boolean continueOnErrors, int exitCode,
                                String[] additionalFiles,
                                int methodsInvalid, int errorCount, int methodsVerified,
                                int methodsSkipped,
                                int[] performanceTicks) implements JVerifyTest {
    @Override
    public Class<? extends Annotation> annotationType() {
        return JVerifyTest.class;
    }
}
