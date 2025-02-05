package org.example.generated;

// Generated Declaration.java:
// Generated from C# class
public class Declaration extends RangeNode {
  private final Name nameNode;

  private final Attributes attributes;

  public Declaration(SourceOrigin origin, Name nameNode, Attributes attributes) {
    super(origin);
    this.nameNode = nameNode;
    this.attributes = attributes;
  }

  public Name getNameNode() {
    return this.nameNode;
  }

  public Attributes getAttributes() {
    return this.attributes;
  }
}
