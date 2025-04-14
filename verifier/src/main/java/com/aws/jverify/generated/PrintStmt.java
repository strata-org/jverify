package com.aws.jverify.generated;

// Generated PrintStmt.java:
// Generated from C# class
import java.util.List;

public class PrintStmt extends Statement {
  private final List<Expression> args;

  public PrintStmt(IOrigin origin, Attributes attributes, List<Expression> args) {
    super(origin, attributes);
    this.args = args;
  }

  public List<Expression> getArgs() {
    return this.args;
  }
}
