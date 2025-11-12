package com.aws.jverify.generated;

public class StaticReceiverExpr extends LiteralExpr {
  private final Type unresolvedType;
  private final boolean isImplicit;

  public StaticReceiverExpr(IOrigin origin, Type type, boolean isImplicit) {
    super(origin, null);
    this.unresolvedType = type;
    this.isImplicit = isImplicit;
  }

  public Type getUnresolvedType() {
    return this.unresolvedType;
  }

  public boolean getIsImplicit() {
    return this.isImplicit;
  }
}
