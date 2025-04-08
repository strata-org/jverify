package com.aws.jverify.generated;

// Generated SeqConstructionExpr.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class SeqConstructionExpr extends Expression {
  @Nullable
  private final Type explicitElementType;

  private final Expression n;

  private final Expression initializer;

  public SeqConstructionExpr(IOrigin origin, Type explicitElementType, Expression n,
      Expression initializer) {
    super(origin);
    this.explicitElementType = explicitElementType;
    this.n = n;
    this.initializer = initializer;
  }

  public Type getExplicitElementType() {
    return this.explicitElementType;
  }

  public Expression getN() {
    return this.n;
  }

  public Expression getInitializer() {
    return this.initializer;
  }
}
