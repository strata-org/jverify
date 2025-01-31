package org.example.generated;

// Generated Declaration.java:
// Generated from C# class
public class Declaration extends RangeNode {
  private final Name name;

  private final Attributes attributes;

  private final Boolean isRefining;

  public Declaration(SourceOrigin origin, Name name, Attributes attributes, Boolean isRefining) {
    super(origin);
    this.name = name;
    this.attributes = attributes;
    this.isRefining = isRefining;
  }

  public Name getName() {
    return this.name;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }

  public Boolean getIsRefining() {
    return this.isRefining;
  }
}
