package com.aws.jverify.generated;

// Generated WhileStmt.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;

public class WhileStmt extends OneBodyLoopStmt {
  private final Expression guard;

  public WhileStmt(IOrigin origin, Attributes attributes, List<AttributedExpression> invariants,
      Specification<Expression> decreases, Specification<FrameExpression> mod, BlockStmt body,
      Expression guard) {
    super(origin, attributes, invariants, decreases, mod, body);
    this.guard = guard;
  }

  public Expression getGuard() {
    return this.guard;
  }
}
