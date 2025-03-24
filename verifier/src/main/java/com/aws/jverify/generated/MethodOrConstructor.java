package com.aws.jverify.generated;

// Generated MethodOrConstructor.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;

public abstract class MethodOrConstructor extends MethodOrFunction {
  private final Specification<FrameExpression> mod;

  public MethodOrConstructor(IOrigin origin, Name nameNode, Attributes attributes, Boolean isGhost,
      IOrigin signatureEllipsis, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<FrameExpression> reads, Specification<Expression> decreases,
      Specification<FrameExpression> mod) {
    super(origin, nameNode, attributes, isGhost, signatureEllipsis, typeArgs, ins, req, ens, reads, decreases);
    this.mod = mod;
  }

  public Specification<FrameExpression> getMod() {
    return this.mod;
  }
}
