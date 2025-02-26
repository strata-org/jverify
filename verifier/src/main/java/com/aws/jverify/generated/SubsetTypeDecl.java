package com.aws.jverify.generated;

// Generated SubsetTypeDecl.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SubsetTypeDecl extends TypeSynonymDecl {
  private final BoundVar var;

  private final Expression constraint;

  private final SubsetTypeDeclWKind witnessKind;

  @Nullable
  private final Expression witness;

  public SubsetTypeDecl(IOrigin origin, Name nameNode, Attributes attributes,
      List<TypeParameter> typeArgs, TypeParameterCharacteristics characteristics, BoundVar var,
      Expression constraint, SubsetTypeDeclWKind witnessKind, Expression witness) {
    super(origin, nameNode, attributes, typeArgs, characteristics);
    this.var = var;
    this.constraint = constraint;
    this.witnessKind = witnessKind;
    this.witness = witness;
  }

  public BoundVar getVar() {
    return this.var;
  }

  public Expression getConstraint() {
    return this.constraint;
  }

  public SubsetTypeDeclWKind getWitnessKind() {
    return this.witnessKind;
  }

  public Expression getWitness() {
    return this.witness;
  }
}
