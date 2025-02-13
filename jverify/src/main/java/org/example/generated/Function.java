package org.example.generated;

// Generated Function.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.example.generated.Specification;

public class Function extends MethodOrFunction {
  private final Boolean isOpaque;

  @Nullable
  private final Formal result;

  private final Type resultType;

  @Nullable
  private final Expression body;

  @Nullable
  private final SourceOrigin byMethodTok;

  @Nullable
  private final BlockStmt byMethodBody;

  @Nullable
  private final SourceOrigin signatureEllipsis;

  public Function(SourceOrigin origin, Name nameNode, Attributes attributes,
      Boolean hasStaticKeyword, Boolean isGhost, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<FrameExpression> reads, Specification<Expression> decreases, Boolean isOpaque,
      Formal result, Type resultType, Expression body, SourceOrigin byMethodTok,
      BlockStmt byMethodBody, SourceOrigin signatureEllipsis) {
    super(origin, nameNode, attributes, hasStaticKeyword, isGhost, typeArgs, ins, req, ens, reads, decreases);
    this.isOpaque = isOpaque;
    this.result = result;
    this.resultType = resultType;
    this.body = body;
    this.byMethodTok = byMethodTok;
    this.byMethodBody = byMethodBody;
    this.signatureEllipsis = signatureEllipsis;
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

  public SourceOrigin getByMethodTok() {
    return this.byMethodTok;
  }

  public BlockStmt getByMethodBody() {
    return this.byMethodBody;
  }

  public SourceOrigin getSignatureEllipsis() {
    return this.signatureEllipsis;
  }
}
