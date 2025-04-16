package com.aws.jverify.generated;

// Generated UnchangedExpr.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class UnchangedExpr extends Expression {
  private final List<FrameExpression> frame;

  @Nullable
  private final String at;

  public UnchangedExpr(IOrigin origin, List<FrameExpression> frame, String at) {
    super(origin);
    this.frame = frame;
    this.at = at;
  }

  public List<FrameExpression> getFrame() {
    return this.frame;
  }

  public String getAt() {
    return this.at;
  }
}
