package com.aws.jverify.generated;

// Generated SourceOrigin.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class SourceOrigin extends IOrigin {
  private final TokenRange entireRange;

  @Nullable
  private final TokenRange reportingRange;

  public SourceOrigin(TokenRange entireRange, TokenRange reportingRange) {
    super();
    this.entireRange = entireRange;
    this.reportingRange = reportingRange;
  }

  public TokenRange getEntireRange() {
    return this.entireRange;
  }

  public TokenRange getReportingRange() {
    return this.reportingRange;
  }
}
