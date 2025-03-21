package com.aws.jverify.generated;

// Generated Function.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Function extends MethodOrFunction {
  private final Boolean hasStaticKeyword;

  private final Boolean isOpaque;

  @Nullable
  private final Formal result;

  private final Type resultType;

  @Nullable
  private final Expression body;

  @Nullable
  private final IOrigin byMethodTok;

  @Nullable
  private final BlockStmt byMethodBody;

  public Function(IOrigin origin, Name nameNode, Attributes attributes, Boolean isGhost,
      IOrigin signatureEllipsis, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<FrameExpression> reads, Specification<Expression> decreases,
      Boolean hasStaticKeyword, Boolean isOpaque, Formal result, Type resultType, Expression body,
      IOrigin byMethodTok, BlockStmt byMethodBody) {
    super(origin, nameNode, attributes, isGhost, signatureEllipsis, typeArgs, ins, req, ens, reads, decreases);
    this.hasStaticKeyword = hasStaticKeyword;
    this.isOpaque = isOpaque;
    this.result = result;
    this.resultType = resultType;
    this.body = body;
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

  public Type getResultType() {
    return this.resultType;
  }

  public Expression getBody() {
    return this.body;
  }

  public IOrigin getByMethodTok() {
    return this.byMethodTok;
  }

  public BlockStmt getByMethodBody() {
    return this.byMethodBody;
  }
}
