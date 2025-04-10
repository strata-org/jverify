package com.aws.jverify.generated;

// Generated OpaqueBlock.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;

public class OpaqueBlock extends BlockStmt {
  private final List<AttributedExpression> ensures;

  private final Specification<FrameExpression> modifies;

  public OpaqueBlock(IOrigin origin, Attributes attributes, List<Label> labels,
      List<Statement> body, List<AttributedExpression> ensures,
      Specification<FrameExpression> modifies) {
    super(origin, attributes, labels, body);
    this.ensures = ensures;
    this.modifies = modifies;
  }

  public List<AttributedExpression> getEnsures() {
    return this.ensures;
  }

  public Specification<FrameExpression> getModifies() {
    return this.modifies;
  }
}
