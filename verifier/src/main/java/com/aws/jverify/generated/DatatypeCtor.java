package com.aws.jverify.generated;

// Generated DatatypeCtor.java:
// Generated from C# class
import java.util.List;

public class DatatypeCtor extends Declaration {
  private final Boolean isGhost;

  private final List<Formal> formals;

  public DatatypeCtor(IOrigin origin, Name nameNode, Attributes attributes, Boolean isGhost,
      List<Formal> formals) {
    super(origin, nameNode, attributes);
    this.isGhost = isGhost;
    this.formals = formals;
  }

  public Boolean getIsGhost() {
    return this.isGhost;
  }

  public List<Formal> getFormals() {
    return this.formals;
  }
}
