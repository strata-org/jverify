package com.aws.jverify.generated;

// Generated NodeWithOrigin.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class NodeWithOrigin extends Node {
  @Nullable
  private final IOrigin origin;

  public NodeWithOrigin(IOrigin origin) {
    super();
    this.origin = origin;
  }

  public IOrigin getOrigin() {
    return this.origin;
  }
}
