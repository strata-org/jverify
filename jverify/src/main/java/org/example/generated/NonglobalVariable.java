package org.example.generated;

// Generated NonglobalVariable.java:
// Generated from C# class
public abstract class NonglobalVariable extends NodeWithComputedRange {
  private final Name nameNode;

  private final Type type;

  private final Boolean isGhost;

  public NonglobalVariable(SourceOrigin origin, Name nameNode, Type type, Boolean isGhost) {
    super(origin);
    this.nameNode = nameNode;
    this.type = type;
    this.isGhost = isGhost;
  }

  public Name getNameNode() {
    return this.nameNode;
  }

  public Type getType() {
    return this.type;
  }

  public Boolean getIsGhost() {
    return this.isGhost;
  }
}
