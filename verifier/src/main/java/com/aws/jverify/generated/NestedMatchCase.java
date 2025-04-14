package com.aws.jverify.generated;

// Generated NestedMatchCase.java:
// Generated from C# class
public abstract class NestedMatchCase extends NodeWithOrigin {
  private final ExtendedPattern pat;

  public NestedMatchCase(IOrigin origin, ExtendedPattern pat) {
    super(origin);
    this.pat = pat;
  }

  public ExtendedPattern getPat() {
    return this.pat;
  }
}
