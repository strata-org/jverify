package com.aws.jverify.generated;

// Generated NonglobalVariable.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class NonglobalVariable extends NodeWithOrigin {
  private final Name nameNode;

  @Nullable
  private final Type syntacticType;

  private final Boolean isGhost;

  public NonglobalVariable(IOrigin origin, Name nameNode, Type syntacticType, Boolean isGhost) {
    super(origin);
    this.nameNode = nameNode;
    this.syntacticType = syntacticType;
    this.isGhost = isGhost;
  }

  public Name getNameNode() {
    return this.nameNode;
  }

  public Type getSyntacticType() {
    return this.syntacticType;
  }

  public Boolean getIsGhost() {
    return this.isGhost;
  }
}
