package com.aws.jverify.generated;

import com.aws.jverify.Nullable;

// Generated Field.java:
// Generated from C# class
public class Field extends MemberDecl {
  private final Type type;

  public Field(IOrigin origin, Name nameNode, @Nullable Attributes attributes, boolean isGhost, Type type) {
    super(origin, nameNode, attributes, isGhost);
    this.type = type;
  }

  public Type getType() {
    return this.type;
  }
}
