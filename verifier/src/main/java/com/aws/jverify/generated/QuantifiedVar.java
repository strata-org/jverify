package com.aws.jverify.generated;

// Generated QuantifiedVar.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class QuantifiedVar extends NodeWithOrigin {
  private final Name nameNode;

  @Nullable
  private final Type syntacticType;

  @Nullable
  private final Expression domain;

  @Nullable
  private final Expression range;

  public QuantifiedVar(IOrigin origin, Name nameNode, Type syntacticType, Expression domain,
      Expression range) {
    super(origin);
    this.nameNode = nameNode;
    this.syntacticType = syntacticType;
    this.domain = domain;
    this.range = range;
  }

  public Name getNameNode() {
    return this.nameNode;
  }

  public Type getSyntacticType() {
    return this.syntacticType;
  }

  public Expression getDomain() {
    return this.domain;
  }

  public Expression getRange() {
    return this.range;
  }
}
