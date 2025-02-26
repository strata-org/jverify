package com.aws.jverify.generated;

// Generated Method.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Method extends MethodOrFunction {
  private final List<Formal> outs;

  private final Specification<FrameExpression> mod;

  @Nullable
  private final BlockStmt body;

  private final Boolean isByMethod;

  public Method(IOrigin origin, Name nameNode, Attributes attributes, Boolean hasStaticKeyword,
      Boolean isGhost, IOrigin signatureEllipsis, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<FrameExpression> reads, Specification<Expression> decreases, List<Formal> outs,
      Specification<FrameExpression> mod, BlockStmt body, Boolean isByMethod) {
    super(origin, nameNode, attributes, hasStaticKeyword, isGhost, signatureEllipsis, typeArgs, ins, req, ens, reads, decreases);
    this.outs = outs;
    this.mod = mod;
    this.body = body;
    this.isByMethod = isByMethod;
  }

  public List<Formal> getOuts() {
    return this.outs;
  }

  public Specification<FrameExpression> getMod() {
    return this.mod;
  }

  public BlockStmt getBody() {
    return this.body;
  }

  public Boolean getIsByMethod() {
    return this.isByMethod;
  }
}
