package com.aws.jverify.generated;

// Generated ITEExpr.java:
// Generated from C# class
public class ITEExpr extends Expression {
  private final Boolean isBindingGuard;

  private final Expression test;

  private final Expression thn;

  private final Expression els;

  public ITEExpr(SourceOrigin origin, Boolean isBindingGuard, Expression test, Expression thn,
      Expression els) {
    super(origin);
    this.isBindingGuard = isBindingGuard;
    this.test = test;
    this.thn = thn;
    this.els = els;
  }

  public Boolean getIsBindingGuard() {
    return this.isBindingGuard;
  }

  public Expression getTest() {
    return this.test;
  }

  public Expression getThn() {
    return this.thn;
  }

  public Expression getEls() {
    return this.els;
  }
}
