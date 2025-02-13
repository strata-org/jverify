package org.example.generated;

// Generated TypeParameterCharacteristics.java:
// Generated from C# class
public class TypeParameterCharacteristics {
  private final TypeParameterEqualitySupportValue equalitySupport;

  private final TypeAutoInitInfo autoInit;

  private final Boolean containsNoReferenceTypes;

  public TypeParameterCharacteristics(TypeParameterEqualitySupportValue equalitySupport,
      TypeAutoInitInfo autoInit, Boolean containsNoReferenceTypes) {
    this.equalitySupport = equalitySupport;
    this.autoInit = autoInit;
    this.containsNoReferenceTypes = containsNoReferenceTypes;
  }

  public TypeParameterEqualitySupportValue getEqualitySupport() {
    return this.equalitySupport;
  }

  public TypeAutoInitInfo getAutoInit() {
    return this.autoInit;
  }

  public Boolean getContainsNoReferenceTypes() {
    return this.containsNoReferenceTypes;
  }
}
