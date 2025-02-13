package com.aws.jverify.generated;

// Generated LocalVariable.java:
// Generated from C# class
public class LocalVariable extends RangeNode {
  private final String name;

  private final Type type;

  private final Boolean isGhost;

  public LocalVariable(SourceOrigin origin, String name, Type type, Boolean isGhost) {
    super(origin);
    this.name = name;
    this.type = type;
    this.isGhost = isGhost;
  }

  public String getName() {
    return this.name;
  }

  public Type getType() {
    return this.type;
  }

  public Boolean getIsGhost() {
    return this.isGhost;
  }
}
