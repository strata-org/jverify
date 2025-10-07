package com.aws.jverify.generated;

// Generated StmtExpr.java:
// Generated from C# class
public class StmtExpr extends Expression {
  private final Statement stmt;

  private final Expression expr;

  public StmtExpr(IOrigin origin, Statement stmt, Expression expr) {
    super(origin);
    this.stmt = stmt;
    this.expr = expr;
  }

  public Statement getStmt() {
    return this.stmt;
  }

  public Expression getExpr() {
    return this.expr;
  }
}
