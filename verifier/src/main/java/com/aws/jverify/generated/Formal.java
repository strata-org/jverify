package com.aws.jverify.generated;

// Generated Formal.java:
// Generated from C# class
import org.checkerframework.checker.nullness.qual.Nullable;

public class Formal extends NonglobalVariable {
  private final Boolean inParam;

  @Nullable
  private final Expression defaultValue;

  @Nullable
  private final Attributes attributes;

  private final Boolean isOld;

  private final Boolean isNameOnly;

  private final Boolean isOlder;

  @Nullable
  private final String nameForCompilation;

  public Formal(IOrigin origin, Name nameNode, Type syntacticType, Boolean isGhost, Boolean inParam,
      Expression defaultValue, Attributes attributes, Boolean isOld, Boolean isNameOnly,
      Boolean isOlder, String nameForCompilation) {
    super(origin, nameNode, syntacticType, isGhost);
    this.inParam = inParam;
    this.defaultValue = defaultValue;
    this.attributes = attributes;
    this.isOld = isOld;
    this.isNameOnly = isNameOnly;
    this.isOlder = isOlder;
    this.nameForCompilation = nameForCompilation;
  }

  public Boolean getInParam() {
    return this.inParam;
  }

  public Expression getDefaultValue() {
    return this.defaultValue;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }

  public Boolean getIsOld() {
    return this.isOld;
  }

  public Boolean getIsNameOnly() {
    return this.isNameOnly;
  }

  public Boolean getIsOlder() {
    return this.isOlder;
  }

  public String getNameForCompilation() {
    return this.nameForCompilation;
  }
}
