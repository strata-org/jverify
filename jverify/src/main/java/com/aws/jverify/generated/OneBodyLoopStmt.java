package com.aws.jverify.generated;

// Generated OneBodyLoopStmt.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;

public abstract class OneBodyLoopStmt extends LoopStmt {
  private final BlockStmt body;

  public OneBodyLoopStmt(SourceOrigin origin, Attributes attributes,
      List<AttributedExpression> invariants, Specification<Expression> decreases,
      Specification<FrameExpression> mod, BlockStmt body) {
    super(origin, attributes, invariants, decreases, mod);
    this.body = body;
  }

  public BlockStmt getBody() {
    return this.body;
  }
}
