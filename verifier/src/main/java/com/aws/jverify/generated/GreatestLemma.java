package com.aws.jverify.generated;

// Generated GreatestLemma.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;

public class GreatestLemma extends ExtremeLemma {
  public GreatestLemma(IOrigin origin, Name nameNode, Attributes attributes,
      Boolean hasStaticKeyword, ExtremePredicateKType typeOfK, List<TypeParameter> typeArgs,
      List<Formal> ins, List<Formal> outs, List<AttributedExpression> req,
      Specification<FrameExpression> reads, Specification<FrameExpression> mod,
      List<AttributedExpression> ens, Specification<Expression> decreases, BlockStmt body,
      IOrigin signatureEllipsis) {
    super(origin, nameNode, attributes, hasStaticKeyword, typeOfK, typeArgs, ins, outs, req, reads, mod, ens, decreases, body, signatureEllipsis);
  }
}
