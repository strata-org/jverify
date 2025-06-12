package com.aws.jverify.generated;

// Generated ExtremePredicate.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class ExtremePredicate extends Declaration {
  private final Boolean hasStaticKeyword;

  private final Boolean isOpaque;

  private final ExtremePredicateKType typeOfK;

  private final List<TypeParameter> typeArgs;

  private final List<Formal> ins;

  @Nullable
  private final Formal result;

  private final List<AttributedExpression> req;

  private final Specification<FrameExpression> reads;

  private final List<AttributedExpression> ens;

  private final Expression body;

  @Nullable
  private final IOrigin signatureEllipsis;

  public ExtremePredicate(IOrigin origin, Name nameNode, Attributes attributes,
      Boolean hasStaticKeyword, Boolean isOpaque, ExtremePredicateKType typeOfK,
      List<TypeParameter> typeArgs, List<Formal> ins, Formal result, List<AttributedExpression> req,
      Specification<FrameExpression> reads, List<AttributedExpression> ens, Expression body,
      IOrigin signatureEllipsis) {
    super(origin, nameNode, attributes);
    this.hasStaticKeyword = hasStaticKeyword;
    this.isOpaque = isOpaque;
    this.typeOfK = typeOfK;
    this.typeArgs = typeArgs;
    this.ins = ins;
    this.result = result;
    this.req = req;
    this.reads = reads;
    this.ens = ens;
    this.body = body;
    this.signatureEllipsis = signatureEllipsis;
  }

  public Boolean getHasStaticKeyword() {
    return this.hasStaticKeyword;
  }

  public Boolean getIsOpaque() {
    return this.isOpaque;
  }

  public ExtremePredicateKType getTypeOfK() {
    return this.typeOfK;
  }

  public List<TypeParameter> getTypeArgs() {
    return this.typeArgs;
  }

  public List<Formal> getIns() {
    return this.ins;
  }

  public Formal getResult() {
    return this.result;
  }

  public List<AttributedExpression> getReq() {
    return this.req;
  }

  public Specification<FrameExpression> getReads() {
    return this.reads;
  }

  public List<AttributedExpression> getEns() {
    return this.ens;
  }

  public Expression getBody() {
    return this.body;
  }

  public IOrigin getSignatureEllipsis() {
    return this.signatureEllipsis;
  }
}
