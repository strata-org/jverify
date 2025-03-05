package com.aws.jverify.generated;

// Generated BlockStmt.java:
// Generated from C# class
import java.util.List;

public class BlockStmt extends Statement {
  private final List<Statement> body;

  public BlockStmt(IOrigin origin, Attributes attributes, List<Statement> body) {
    super(origin, attributes);
    this.body = body;
  }

  public List<Statement> getBody() {
    return this.body;
  }
}
