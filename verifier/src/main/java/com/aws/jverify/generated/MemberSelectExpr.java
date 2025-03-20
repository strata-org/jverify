package com.aws.jverify.generated;

// Generated MemberSelectExpr.java:
// Generated from C# class
public class MemberSelectExpr extends Expression {
  private final Expression obj;

  private final Name memberName;

  public MemberSelectExpr(IOrigin origin, Expression obj, Name memberName) {
    super(origin);
    this.obj = obj;
    this.memberName = memberName;
  }

  public Expression getObj() {
    return this.obj;
  }

  public Name getMemberName() {
    return this.memberName;
  }
}
