package com.aws.jverify.generated;

// Generated MethodOrFunction.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class MethodOrFunction extends MemberDecl {
  @Nullable
  private final IOrigin signatureEllipsis;

  private final List<TypeParameter> typeArgs;

  private final List<Formal> ins;

  private final List<AttributedExpression> req;

  private final List<AttributedExpression> ens;

  private final Specification<FrameExpression> reads;

  private final Specification<Expression> decreases;

  public MethodOrFunction(IOrigin origin, Name nameNode, Attributes attributes,
      Boolean hasStaticKeyword, Boolean isGhost, IOrigin signatureEllipsis,
      List<TypeParameter> typeArgs, List<Formal> ins, List<AttributedExpression> req,
      List<AttributedExpression> ens, Specification<FrameExpression> reads,
      Specification<Expression> decreases) {
    super(origin, nameNode, attributes, hasStaticKeyword, isGhost);
    this.signatureEllipsis = signatureEllipsis;
    this.typeArgs = typeArgs;
    this.ins = ins;
    this.req = req;
    this.ens = ens;
    this.reads = reads;
    this.decreases = decreases;
  }

  public IOrigin getSignatureEllipsis() {
    return this.signatureEllipsis;
  }

  public List<TypeParameter> getTypeArgs() {
    return this.typeArgs;
  }

  public List<Formal> getIns() {
    return this.ins;
  }

  public List<AttributedExpression> getReq() {
    return this.req;
  }

  public List<AttributedExpression> getEns() {
    return this.ens;
  }

  public Specification<FrameExpression> getReads() {
    return this.reads;
  }

  public Specification<Expression> getDecreases() {
    return this.decreases;
  }
}
