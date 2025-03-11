package com.aws.jverify.generated;

// Generated SeqSelectExpr.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class SeqSelectExpr extends Expression {
  private final Boolean selectOne;

  private final Expression seq;

  @Nullable
  private final Expression e0;

  @Nullable
  private final Expression e1;

  @Nullable
  private final Token closeParen;

  public SeqSelectExpr(IOrigin origin, Boolean selectOne, Expression seq, Expression e0,
      Expression e1, Token closeParen) {
    super(origin);
    this.selectOne = selectOne;
    this.seq = seq;
    this.e0 = e0;
    this.e1 = e1;
    this.closeParen = closeParen;
  }

  public Boolean getSelectOne() {
    return this.selectOne;
  }

  public Expression getSeq() {
    return this.seq;
  }

  public Expression getE0() {
    return this.e0;
  }

  public Expression getE1() {
    return this.e1;
  }

  public Token getCloseParen() {
    return this.closeParen;
  }
}
