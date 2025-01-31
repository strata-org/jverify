package org.example.generated;

// Generated MethodOrFunction.java:
// Generated from C# class
import java.util.List;
import org.example.generated.Specification;

public class MethodOrFunction extends MemberDecl {
  private final List<TypeParameter> typeArgs;

  private final List<Formal> ins;

  private final List<AttributedExpression> req;

  private final List<AttributedExpression> ens;

  private final Specification<Expression> decreases;

  public MethodOrFunction(SourceOrigin origin, Name name, Attributes attributes,
      Boolean hasStaticKeyword, Boolean isGhost, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<Expression> decreases) {
    super(origin, name, attributes, hasStaticKeyword, isGhost);
    this.typeArgs = typeArgs;
    this.ins = ins;
    this.req = req;
    this.ens = ens;
    this.decreases = decreases;
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

  public Specification<Expression> getDecreases() {
    return this.decreases;
  }
}
