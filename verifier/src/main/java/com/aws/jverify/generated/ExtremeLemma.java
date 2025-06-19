package com.aws.jverify.generated;

// Generated ExtremeLemma.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class ExtremeLemma extends Declaration {
  private final Boolean hasStaticKeyword;

  private final ExtremePredicateKType typeOfK;

  private final List<TypeParameter> typeArgs;

  private final List<Formal> ins;

  private final List<Formal> outs;

  private final List<AttributedExpression> req;

  private final Specification<FrameExpression> reads;

  private final Specification<FrameExpression> mod;

  private final List<AttributedExpression> ens;

  private final Specification<Expression> decreases;

  private final BlockStmt body;

  @Nullable
  private final IOrigin signatureEllipsis;

  public ExtremeLemma(IOrigin origin, Name nameNode, Attributes attributes,
      Boolean hasStaticKeyword, ExtremePredicateKType typeOfK, List<TypeParameter> typeArgs,
      List<Formal> ins, List<Formal> outs, List<AttributedExpression> req,
      Specification<FrameExpression> reads, Specification<FrameExpression> mod,
      List<AttributedExpression> ens, Specification<Expression> decreases, BlockStmt body,
      IOrigin signatureEllipsis) {
    super(origin, nameNode, attributes);
    this.hasStaticKeyword = hasStaticKeyword;
    this.typeOfK = typeOfK;
    this.typeArgs = typeArgs;
    this.ins = ins;
    this.outs = outs;
    this.req = req;
    this.reads = reads;
    this.mod = mod;
    this.ens = ens;
    this.decreases = decreases;
    this.body = body;
    this.signatureEllipsis = signatureEllipsis;
  }

  public Boolean getHasStaticKeyword() {
    return this.hasStaticKeyword;
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

  public List<Formal> getOuts() {
    return this.outs;
  }

  public List<AttributedExpression> getReq() {
    return this.req;
  }

  public Specification<FrameExpression> getReads() {
    return this.reads;
  }

  public Specification<FrameExpression> getMod() {
    return this.mod;
  }

  public List<AttributedExpression> getEns() {
    return this.ens;
  }

  public Specification<Expression> getDecreases() {
    return this.decreases;
  }

  public BlockStmt getBody() {
    return this.body;
  }

  public IOrigin getSignatureEllipsis() {
    return this.signatureEllipsis;
  }
}
