package com.aws.jverify.generated;

// Generated OneBodyLoopStmt.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class OneBodyLoopStmt extends LoopStmt {
  @Nullable
  private final BlockStmt body;

  public OneBodyLoopStmt(IOrigin origin, Attributes attributes, List<Label> labels,
      List<AttributedExpression> invariants, Specification<Expression> decreases,
      Specification<FrameExpression> mod, BlockStmt body) {
    super(origin, attributes, labels, invariants, decreases, mod);
    this.body = body;
  }

  public BlockStmt getBody() {
    return this.body;
  }
}
