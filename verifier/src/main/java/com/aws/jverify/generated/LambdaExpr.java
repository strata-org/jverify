package com.aws.jverify.generated;

// Generated LambdaExpr.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;

public class LambdaExpr extends ComprehensionExpr {
  private final Specification<FrameExpression> reads;

  public LambdaExpr(IOrigin origin, List<BoundVar> boundVars, Expression range, Expression term,
      Attributes attributes, Specification<FrameExpression> reads) {
    super(origin, boundVars, range, term, attributes);
    this.reads = reads;
  }

  public Specification<FrameExpression> getReads() {
    return this.reads;
  }
}
