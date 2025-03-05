package com.aws.jverify.generated;

// Generated VarDeclStmt.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class VarDeclStmt extends Statement {
  private final List<LocalVariable> locals;

  @Nullable
  private final ConcreteAssignStatement assign;

  public VarDeclStmt(IOrigin origin, Attributes attributes, List<LocalVariable> locals,
      ConcreteAssignStatement assign) {
    super(origin, attributes);
    this.locals = locals;
    this.assign = assign;
  }

  public List<LocalVariable> getLocals() {
    return this.locals;
  }

  public ConcreteAssignStatement getAssign() {
    return this.assign;
  }
}
