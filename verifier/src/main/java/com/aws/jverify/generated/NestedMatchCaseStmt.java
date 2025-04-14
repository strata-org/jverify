package com.aws.jverify.generated;

// Generated NestedMatchCaseStmt.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NestedMatchCaseStmt extends NestedMatchCase {
  private final List<Statement> body;

  @Nullable
  private final Attributes attributes;

  public NestedMatchCaseStmt(IOrigin origin, ExtendedPattern pat, List<Statement> body,
      Attributes attributes) {
    super(origin, pat);
    this.body = body;
    this.attributes = attributes;
  }

  public List<Statement> getBody() {
    return this.body;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
