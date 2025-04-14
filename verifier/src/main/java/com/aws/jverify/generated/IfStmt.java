package com.aws.jverify.generated;

// Generated IfStmt.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class IfStmt extends LabeledStatement {
  private final Boolean isBindingGuard;

  @Nullable
  private final Expression guard;

  private final BlockStmt thn;

  @Nullable
  private final Statement els;

  public IfStmt(IOrigin origin, Attributes attributes, List<Label> labels, Boolean isBindingGuard,
      Expression guard, BlockStmt thn, Statement els) {
    super(origin, attributes, labels);
    this.isBindingGuard = isBindingGuard;
    this.guard = guard;
    this.thn = thn;
    this.els = els;
  }

  public Boolean getIsBindingGuard() {
    return this.isBindingGuard;
  }

  public Expression getGuard() {
    return this.guard;
  }

  public BlockStmt getThn() {
    return this.thn;
  }

  public Statement getEls() {
    return this.els;
  }
}
