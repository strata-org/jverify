package com.aws.jverify.testengine;

import java.lang.annotation.Annotation;

public record JVerifyTestRecord(String skip, boolean verifyByDefault, boolean useBuiltinContracts,
                                boolean continueOnErrors, int exitCode, int dafnyVerified, int dafnyErrors,
                                String[] additionalFiles, boolean verifyPrintedDafny) implements JVerifyTest {
    @Override
    public Class<? extends Annotation> annotationType() {
        return JVerifyTest.class;
    }
}
