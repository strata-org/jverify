package com.aws.jverify.generated;

// Generated Declaration.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class Declaration extends NodeWithOrigin {
  private final Name nameNode;

  @Nullable
  private final Attributes attributes;

  public Declaration(IOrigin origin, Name nameNode, Attributes attributes) {
    super(origin);
    this.nameNode = nameNode;
    this.attributes = attributes;
  }

  public Name getNameNode() {
    return this.nameNode;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
