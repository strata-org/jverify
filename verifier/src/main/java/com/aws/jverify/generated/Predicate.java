package com.aws.jverify.generated;

// Generated Predicate.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Predicate extends MethodOrFunction {
  private final Boolean hasStaticKeyword;

  private final Boolean isOpaque;

  @Nullable
  private final Formal result;

  @Nullable
  private final Expression body;

  private final PredicateBodyOriginKind bodyOrigin;

  @Nullable
  private final IOrigin byMethodTok;

  @Nullable
  private final BlockStmt byMethodBody;

  public Predicate(IOrigin origin, Name nameNode, Attributes attributes, Boolean isGhost,
      IOrigin signatureEllipsis, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<FrameExpression> reads, Specification<Expression> decreases,
      Boolean hasStaticKeyword, Boolean isOpaque, Formal result, Expression body,
      PredicateBodyOriginKind bodyOrigin, IOrigin byMethodTok, BlockStmt byMethodBody) {
    super(origin, nameNode, attributes, isGhost, signatureEllipsis, typeArgs, ins, req, ens, reads, decreases);
    this.hasStaticKeyword = hasStaticKeyword;
    this.isOpaque = isOpaque;
    this.result = result;
    this.body = body;
    this.bodyOrigin = bodyOrigin;
    this.byMethodTok = byMethodTok;
    this.byMethodBody = byMethodBody;
  }

  public Boolean getHasStaticKeyword() {
    return this.hasStaticKeyword;
  }

  public Boolean getIsOpaque() {
    return this.isOpaque;
  }

  public Formal getResult() {
    return this.result;
  }

  public Expression getBody() {
    return this.body;
  }

  public PredicateBodyOriginKind getBodyOrigin() {
    return this.bodyOrigin;
  }

  public IOrigin getByMethodTok() {
    return this.byMethodTok;
  }

  public BlockStmt getByMethodBody() {
    return this.byMethodBody;
  }
}
