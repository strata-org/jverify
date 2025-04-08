package com.aws.jverify.generated;

// Generated NodeWithOrigin.java:
// Generated from C# class
public abstract class NodeWithOrigin extends Node {
  private final IOrigin origin;

  public NodeWithOrigin(IOrigin origin) {
    super();
    this.origin = origin;
  }

  public IOrigin getOrigin() {
    return this.origin;
  }
}
