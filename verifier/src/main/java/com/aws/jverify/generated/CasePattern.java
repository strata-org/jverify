package com.aws.jverify.generated;

// Generated CasePattern.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class CasePattern<VT> extends NodeWithOrigin {
  private final String id;

  @Nullable
  private final VT var;

  public CasePattern(IOrigin origin, String id, VT var) {
    super(origin);
    this.id = id;
    this.var = var;
  }

  public String getId() {
    return this.id;
  }

  public VT getVar() {
    return this.var;
  }
}
