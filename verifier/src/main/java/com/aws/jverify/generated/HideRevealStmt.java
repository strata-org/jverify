package com.aws.jverify.generated;

// Generated HideRevealStmt.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class HideRevealStmt extends Statement {
  @Nullable
  private final List<Expression> exprs;

  private final HideRevealCmdModes mode;

  public HideRevealStmt(IOrigin origin, Attributes attributes, List<Expression> exprs,
      HideRevealCmdModes mode) {
    super(origin, attributes);
    this.exprs = exprs;
    this.mode = mode;
  }

  public List<Expression> getExprs() {
    return this.exprs;
  }

  public HideRevealCmdModes getMode() {
    return this.mode;
  }
}
