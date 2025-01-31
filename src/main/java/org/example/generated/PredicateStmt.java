package org.example.generated;

// Generated PredicateStmt.java:
// Generated from C# class
public class PredicateStmt extends Statement {
  private final Expression expr;

  public PredicateStmt(SourceOrigin origin, Attributes attributes, Expression expr) {
    super(origin, attributes);
    this.expr = expr;
  }

  public Expression getExpr() {
    return this.expr;
  }
}
