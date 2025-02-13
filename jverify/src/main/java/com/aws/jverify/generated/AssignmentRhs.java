package com.aws.jverify.generated;

// Generated AssignmentRhs.java:
// Generated from C# class
public abstract class AssignmentRhs extends NodeWithComputedRange {
  private final Attributes attributes;

  public AssignmentRhs(SourceOrigin origin, Attributes attributes) {
    super(origin);
    this.attributes = attributes;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
