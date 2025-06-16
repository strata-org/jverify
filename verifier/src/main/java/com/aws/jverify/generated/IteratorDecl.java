package com.aws.jverify.generated;

// Generated IteratorDecl.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class IteratorDecl extends TopLevelDecl {
  private final List<Formal> ins;

  private final List<Formal> outs;

  private final Specification<FrameExpression> reads;

  private final Specification<FrameExpression> modifies;

  private final Specification<Expression> decreases;

  private final List<AttributedExpression> requires;

  private final List<AttributedExpression> ensures;

  private final List<AttributedExpression> yieldRequires;

  private final List<AttributedExpression> yieldEnsures;

  private final BlockStmt body;

  @Nullable
  private final IOrigin signatureEllipsis;

  public IteratorDecl(IOrigin origin, Name nameNode, Attributes attributes,
      List<TypeParameter> typeArgs, List<Formal> ins, List<Formal> outs,
      Specification<FrameExpression> reads, Specification<FrameExpression> modifies,
      Specification<Expression> decreases, List<AttributedExpression> requires,
      List<AttributedExpression> ensures, List<AttributedExpression> yieldRequires,
      List<AttributedExpression> yieldEnsures, BlockStmt body, IOrigin signatureEllipsis) {
    super(origin, nameNode, attributes, typeArgs);
    this.ins = ins;
    this.outs = outs;
    this.reads = reads;
    this.modifies = modifies;
    this.decreases = decreases;
    this.requires = requires;
    this.ensures = ensures;
    this.yieldRequires = yieldRequires;
    this.yieldEnsures = yieldEnsures;
    this.body = body;
    this.signatureEllipsis = signatureEllipsis;
  }

  public List<Formal> getIns() {
    return this.ins;
  }

  public List<Formal> getOuts() {
    return this.outs;
  }

  public Specification<FrameExpression> getReads() {
    return this.reads;
  }

  public Specification<FrameExpression> getModifies() {
    return this.modifies;
  }

  public Specification<Expression> getDecreases() {
    return this.decreases;
  }

  public List<AttributedExpression> getRequires() {
    return this.requires;
  }

  public List<AttributedExpression> getEnsures() {
    return this.ensures;
  }

  public List<AttributedExpression> getYieldRequires() {
    return this.yieldRequires;
  }

  public List<AttributedExpression> getYieldEnsures() {
    return this.yieldEnsures;
  }

  public BlockStmt getBody() {
    return this.body;
  }

  public IOrigin getSignatureEllipsis() {
    return this.signatureEllipsis;
  }
}
