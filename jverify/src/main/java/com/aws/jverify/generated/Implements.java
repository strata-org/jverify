package com.aws.jverify.generated;

// Generated Implements.java:
// Generated from C# class
public class Implements {
  private final ImplementationKind Kind;

  private final ModuleQualifiedId Target;

  public Implements(ImplementationKind Kind, ModuleQualifiedId Target) {
    this.Kind = Kind;
    this.Target = Target;
  }

  public ImplementationKind getKind() {
    return this.Kind;
  }

  public ModuleQualifiedId getTarget() {
    return this.Target;
  }
}
