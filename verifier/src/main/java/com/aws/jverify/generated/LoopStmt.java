package com.aws.jverify.generated;

// Generated LoopStmt.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;

public abstract class LoopStmt extends LabeledStatement {
  private final List<AttributedExpression> invariants;

  private final Specification<Expression> decreases;

  private final Specification<FrameExpression> mod;

  public LoopStmt(IOrigin origin, Attributes attributes, List<Label> labels,
      List<AttributedExpression> invariants, Specification<Expression> decreases,
      Specification<FrameExpression> mod) {
    super(origin, attributes, labels);
    this.invariants = invariants;
    this.decreases = decreases;
    this.mod = mod;
  }

  public List<AttributedExpression> getInvariants() {
    return this.invariants;
  }

  public Specification<Expression> getDecreases() {
    return this.decreases;
  }

  public Specification<FrameExpression> getMod() {
    return this.mod;
  }
}
