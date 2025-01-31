package org.example.generated;

// Generated Declaration.java:
// Generated from C# class
public class Declaration extends RangeNode {
  private final Name name;

  private final Attributes attributes;

  public Declaration(SourceOrigin origin, Name name, Attributes attributes) {
    super(origin);
    this.name = name;
    this.attributes = attributes;
  }

  public Name getName() {
    return this.name;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
