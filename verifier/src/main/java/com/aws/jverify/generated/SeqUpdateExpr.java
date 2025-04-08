package com.aws.jverify.generated;

// Generated SeqUpdateExpr.java:
// Generated from C# class
public class SeqUpdateExpr extends Expression {
  private final Expression seq;

  private final Expression index;

  private final Expression value;

  public SeqUpdateExpr(IOrigin origin, Expression seq, Expression index, Expression value) {
    super(origin);
    this.seq = seq;
    this.index = index;
    this.value = value;
  }

  public Expression getSeq() {
    return this.seq;
  }

  public Expression getIndex() {
    return this.index;
  }

  public Expression getValue() {
    return this.value;
  }
}
