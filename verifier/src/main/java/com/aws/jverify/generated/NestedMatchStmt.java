package com.aws.jverify.generated;

// Generated NestedMatchStmt.java:
// Generated from C# class
import java.util.List;

public class NestedMatchStmt extends Statement {
  private final Expression source;

  private final List<NestedMatchCaseStmt> cases;

  private final Boolean usesOptionalBraces;

  public NestedMatchStmt(IOrigin origin, Attributes attributes, Expression source,
      List<NestedMatchCaseStmt> cases, Boolean usesOptionalBraces) {
    super(origin, attributes);
    this.source = source;
    this.cases = cases;
    this.usesOptionalBraces = usesOptionalBraces;
  }

  public Expression getSource() {
    return this.source;
  }

  public List<NestedMatchCaseStmt> getCases() {
    return this.cases;
  }

  public Boolean getUsesOptionalBraces() {
    return this.usesOptionalBraces;
  }
}
