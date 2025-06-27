package com.aws.jverify.generated;

// Generated LetExpr.java:
// Generated from C# class
import com.aws.jverify.generated.CasePattern;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class LetExpr extends Expression {
  private final List<CasePattern<BoundVar>> lhss;

  private final List<Expression> rhss;

  private final Expression body;

  private final Boolean exact;

  @Nullable
  private final Attributes attributes;

  public LetExpr(IOrigin origin, List<CasePattern<BoundVar>> lhss, List<Expression> rhss,
      Expression body, Boolean exact, Attributes attributes) {
    super(origin);
    this.lhss = lhss;
    this.rhss = rhss;
    this.body = body;
    this.exact = exact;
    this.attributes = attributes;
  }

  public List<CasePattern<BoundVar>> getLhss() {
    return this.lhss;
  }

  public List<Expression> getRhss() {
    return this.rhss;
  }

  public Expression getBody() {
    return this.body;
  }

  public Boolean getExact() {
    return this.exact;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
