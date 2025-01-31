package org.example.generated;

// Generated Formal.java:
// Generated from C# class
public class Formal extends NonglobalVariable {
  private final Boolean inParam;

  private final Expression defaultValue;

  private final Attributes attributes;

  private final Boolean isOld;

  private final Boolean isNameOnly;

  private final Boolean isOlder;

  private final String nameForCompilation;

  public Formal(SourceOrigin origin, Name nameNode, Type type, Boolean isGhost, Boolean inParam,
      Expression defaultValue, Attributes attributes, Boolean isOld, Boolean isNameOnly,
      Boolean isOlder, String nameForCompilation) {
    super(origin, nameNode, type, isGhost);
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
