package com.aws.jverify.generated;

// Generated Statement.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class Statement extends RangeNode {
  @Nullable
  private final Attributes attributes;

  public Statement(IOrigin origin, Attributes attributes) {
    super(origin);
    this.attributes = attributes;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
