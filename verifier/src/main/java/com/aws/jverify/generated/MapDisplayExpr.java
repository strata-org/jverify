package com.aws.jverify.generated;

// Generated MapDisplayExpr.java:
// Generated from C# class
import java.util.List;

public class MapDisplayExpr extends Expression {
  private final Boolean finite;

  private final List<MapDisplayEntry> elements;

  public MapDisplayExpr(IOrigin origin, Boolean finite, List<MapDisplayEntry> elements) {
    super(origin);
    this.finite = finite;
    this.elements = elements;
  }

  public Boolean getFinite() {
    return this.finite;
  }

  public List<MapDisplayEntry> getElements() {
    return this.elements;
  }
}
