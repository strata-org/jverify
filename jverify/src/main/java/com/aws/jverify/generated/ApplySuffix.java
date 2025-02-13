package com.aws.jverify.generated;

// Generated ApplySuffix.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class ApplySuffix extends SuffixExpr {
  @Nullable
  private final SourceOrigin atTok;

  private final ActualBindings bindings;

  @Nullable
  private final Token closeParen;

  public ApplySuffix(SourceOrigin origin, Expression lhs, SourceOrigin atTok,
      ActualBindings bindings, Token closeParen) {
    super(origin, lhs);
    this.atTok = atTok;
    this.bindings = bindings;
    this.closeParen = closeParen;
  }

  public SourceOrigin getAtTok() {
    return this.atTok;
  }

  public ActualBindings getBindings() {
    return this.bindings;
  }

  public Token getCloseParen() {
    return this.closeParen;
  }
}
