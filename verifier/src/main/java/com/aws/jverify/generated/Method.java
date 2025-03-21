package com.aws.jverify.generated;

// Generated Method.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Method extends MethodOrConstructor {
  private final Boolean hasStaticKeyword;

  private final List<Formal> outs;

  @Nullable
  private final BlockStmt body;

  private final Boolean isByMethod;

  public Method(IOrigin origin, Name nameNode, Attributes attributes, Boolean isGhost,
      IOrigin signatureEllipsis, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<FrameExpression> reads, Specification<Expression> decreases,
      Specification<FrameExpression> mod, Boolean hasStaticKeyword, List<Formal> outs,
      BlockStmt body, Boolean isByMethod) {
    super(origin, nameNode, attributes, isGhost, signatureEllipsis, typeArgs, ins, req, ens, reads, decreases, mod);
    this.hasStaticKeyword = hasStaticKeyword;
    this.outs = outs;
    this.body = body;
    this.isByMethod = isByMethod;
  }

  public Boolean getHasStaticKeyword() {
    return this.hasStaticKeyword;
  }

  public List<Formal> getOuts() {
    return this.outs;
  }

  public BlockStmt getBody() {
    return this.body;
  }

  public Boolean getIsByMethod() {
    return this.isByMethod;
  }
}
