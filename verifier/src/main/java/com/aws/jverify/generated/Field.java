package com.aws.jverify.generated;

// Generated Field.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class Field extends MemberDecl {
  @Nullable
  private final Type explicitType;

  public Field(IOrigin origin, Name nameNode, Attributes attributes, Boolean isGhost,
      Type explicitType) {
    super(origin, nameNode, attributes, isGhost);
    this.explicitType = explicitType;
  }

  public Type getExplicitType() {
    return this.explicitType;
  }
}
