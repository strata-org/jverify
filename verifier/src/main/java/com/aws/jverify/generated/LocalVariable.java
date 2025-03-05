package com.aws.jverify.generated;

// Generated LocalVariable.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class LocalVariable extends RangeNode {
  private final String name;

  @Nullable
  private final Type syntacticType;

  private final Boolean isGhost;

  public LocalVariable(IOrigin origin, String name, Type syntacticType, Boolean isGhost) {
    super(origin);
    this.name = name;
    this.syntacticType = syntacticType;
    this.isGhost = isGhost;
  }

  public String getName() {
    return this.name;
  }

  public Type getSyntacticType() {
    return this.syntacticType;
  }

  public Boolean getIsGhost() {
    return this.isGhost;
  }
}
