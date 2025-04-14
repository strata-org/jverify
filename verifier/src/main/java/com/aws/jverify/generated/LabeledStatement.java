package com.aws.jverify.generated;

// Generated LabeledStatement.java:
// Generated from C# class
import java.util.List;

public class LabeledStatement extends Statement {
  private final List<Label> labels;

  public LabeledStatement(IOrigin origin, Attributes attributes, List<Label> labels) {
    super(origin, attributes);
    this.labels = labels;
  }

  public List<Label> getLabels() {
    return this.labels;
  }
}
