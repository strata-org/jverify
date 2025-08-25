package com.aws.jverify.generated;

// Generated DividedBlockStmt.java:
// Generated from C# class
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class DividedBlockStmt extends BlockLikeStmt {
  private final List<Statement> bodyInit;

  @Nullable
  private final IOrigin separatorTok;

  private final List<Statement> bodyProper;

  public DividedBlockStmt(IOrigin origin, Attributes attributes, List<Label> labels,
      List<Statement> bodyInit, IOrigin separatorTok, List<Statement> bodyProper) {
    super(origin, attributes, labels);
    this.bodyInit = bodyInit;
    this.separatorTok = separatorTok;
    this.bodyProper = bodyProper;
  }

  public List<Statement> getBodyInit() {
    return this.bodyInit;
  }

  public IOrigin getSeparatorTok() {
    return this.separatorTok;
  }

  public List<Statement> getBodyProper() {
    return this.bodyProper;
  }
}
