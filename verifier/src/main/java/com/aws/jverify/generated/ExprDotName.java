package com.aws.jverify.generated;

// Generated ExprDotName.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ExprDotName extends SuffixExpr {
  private final Name suffixNameNode;

  @Nullable
  private final List<Type> optTypeArguments;

  public ExprDotName(IOrigin origin, Expression lhs, Name suffixNameNode,
      List<Type> optTypeArguments) {
    super(origin, lhs);
    this.suffixNameNode = suffixNameNode;
    this.optTypeArguments = optTypeArguments;
  }

  public Name getSuffixNameNode() {
    return this.suffixNameNode;
  }

  public List<Type> getOptTypeArguments() {
    return this.optTypeArguments;
  }
}
