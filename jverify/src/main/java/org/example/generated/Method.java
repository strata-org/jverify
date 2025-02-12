package org.example.generated;

// Generated Method.java:
// Generated from C# class
import java.util.List;
import org.example.generated.Specification;

public class Method extends MethodOrFunction {
  private final List<Formal> outs;

  private final Specification<FrameExpression> mod;

  private final BlockStmt body;

  private final SourceOrigin signatureEllipsis;

  private final Boolean isByMethod;

  public Method(SourceOrigin origin, Name nameNode, Attributes attributes, Boolean hasStaticKeyword,
      Boolean isGhost, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<FrameExpression> reads, Specification<Expression> decreases, List<Formal> outs,
      Specification<FrameExpression> mod, BlockStmt body, SourceOrigin signatureEllipsis,
      Boolean isByMethod) {
    super(origin, nameNode, attributes, hasStaticKeyword, isGhost, typeArgs, ins, req, ens, reads, decreases);
    this.outs = outs;
    this.mod = mod;
    this.body = body;
    this.signatureEllipsis = signatureEllipsis;
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

  public SourceOrigin getSignatureEllipsis() {
    return this.signatureEllipsis;
  }

  public Boolean getIsByMethod() {
    return this.isByMethod;
  }
}
