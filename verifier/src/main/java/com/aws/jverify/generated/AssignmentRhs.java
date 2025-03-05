package com.aws.jverify.generated;

// Generated AssignmentRhs.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class AssignmentRhs extends NodeWithComputedRange {
  @Nullable
  private final Attributes attributes;

  public AssignmentRhs(IOrigin origin, Attributes attributes) {
    super(origin);
    this.attributes = attributes;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
