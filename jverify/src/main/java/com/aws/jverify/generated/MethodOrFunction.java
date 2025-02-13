package com.aws.jverify.generated;

// Generated MethodOrFunction.java:
// Generated from C# class
import com.aws.jverify.generated.Specification;
import java.util.List;

public abstract class MethodOrFunction extends MemberDecl {
  private final List<TypeParameter> typeArgs;

  private final List<Formal> ins;

  private final List<AttributedExpression> req;

  private final List<AttributedExpression> ens;

  private final Specification<FrameExpression> reads;

  private final Specification<Expression> decreases;

  public MethodOrFunction(SourceOrigin origin, Name nameNode, Attributes attributes,
      Boolean hasStaticKeyword, Boolean isGhost, List<TypeParameter> typeArgs, List<Formal> ins,
      List<AttributedExpression> req, List<AttributedExpression> ens,
      Specification<FrameExpression> reads, Specification<Expression> decreases) {
    super(origin, nameNode, attributes, hasStaticKeyword, isGhost);
    this.typeArgs = typeArgs;
    this.ins = ins;
    this.req = req;
    this.ens = ens;
    this.reads = reads;
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

  public Specification<FrameExpression> getReads() {
    return this.reads;
  }

  public Specification<Expression> getDecreases() {
    return this.decreases;
  }
}
