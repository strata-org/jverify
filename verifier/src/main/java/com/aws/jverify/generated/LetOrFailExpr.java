package com.aws.jverify.generated;

// Generated LetOrFailExpr.java:
// Generated from C# class
import com.aws.jverify.generated.CasePattern;
import org.checkerframework.checker.nullness.qual.Nullable;

public class LetOrFailExpr extends ConcreteSyntaxExpression {
  @Nullable
  private final CasePattern<BoundVar> lhs;

  private final Expression rhs;

  private final Expression body;

  public LetOrFailExpr(IOrigin origin, CasePattern<BoundVar> lhs, Expression rhs, Expression body) {
    super(origin);
    this.lhs = lhs;
    this.rhs = rhs;
    this.body = body;
  }

  public CasePattern<BoundVar> getLhs() {
    return this.lhs;
  }

  public Expression getRhs() {
    return this.rhs;
  }

  public Expression getBody() {
    return this.body;
  }
}
