package com.aws.jverify.generated;

// Generated CasePattern.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class CasePattern<VT> extends NodeWithOrigin {
  private final String id;

  @Nullable
  private final VT var;

  @Nullable
  private final List<com.aws.jverify.generated.CasePattern<VT>> arguments;

  public CasePattern(IOrigin origin, String id, VT var,
      List<com.aws.jverify.generated.CasePattern<VT>> arguments) {
    super(origin);
    this.id = id;
    this.var = var;
    this.arguments = arguments;
  }

  public String getId() {
    return this.id;
  }

  public VT getVar() {
    return this.var;
  }

  public List<com.aws.jverify.generated.CasePattern<VT>> getArguments() {
    return this.arguments;
  }
}
