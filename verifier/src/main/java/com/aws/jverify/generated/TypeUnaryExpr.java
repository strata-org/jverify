package com.aws.jverify.generated;

// Generated TypeUnaryExpr.java:
// Generated from C# class
public abstract class TypeUnaryExpr extends UnaryExpr {
  private final Type toType;

  public TypeUnaryExpr(IOrigin origin, Expression e, Type toType) {
    super(origin, e);
    this.toType = toType;
  }

  public Type getToType() {
    return this.toType;
  }
}
