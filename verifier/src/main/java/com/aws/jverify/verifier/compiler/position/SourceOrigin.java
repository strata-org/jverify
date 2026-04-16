package com.aws.jverify.verifier.compiler.position;

import org.checkerframework.checker.nullness.qual.Nullable;

public class SourceOrigin extends IOrigin {
    private final TokenRange entireRange;
    @Nullable private final TokenRange reportingRange;

    public SourceOrigin(TokenRange entireRange, @Nullable TokenRange reportingRange) {
        this.entireRange = entireRange;
        this.reportingRange = reportingRange;
    }

    public TokenRange getEntireRange() { return entireRange; }
    public @Nullable TokenRange getReportingRange() { return reportingRange; }
}
