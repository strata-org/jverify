package com.aws.jverify.generated;

// Generated Constructor.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Constructor extends MethodOrConstructor {
  @Nullable
  private final DividedBlockStmt body;

  public Constructor(IOrigin origin, Name nameNode, Attributes attributes, Boolean isGhost,
      IOrigin signatureEllipsis, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<FrameExpression> reads, Specification<Expression> decreases,
      Specification<FrameExpression> mod, DividedBlockStmt body) {
    super(origin, nameNode, attributes, isGhost, signatureEllipsis, typeArgs, ins, req, ens, reads, decreases, mod);
    this.body = body;
  }

  public DividedBlockStmt getBody() {
    return this.body;
  }
}
