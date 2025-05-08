# Nullable

JVerify allows detecting all possible `NullReferenceException`s in your Java code. To make it easier to check this, JVerify requires that any reference variable which can be null, is annotated with `@Nullable`. Example:
```java
class C {
  int x;
  
  static void nonNull(C c) {
    var x = c.x; // pass
  }
  
  static void nullable(@Nullable C c) {
    if (c != null) {
        var x = c.x; // pass  
    }
    var y = c.x; // error: could not prove that c is not null 
  }
  
  static void caller() {
    nonNull(null); // error: could not prove that argument 'null' is not null
    nullable(null); // pass
  }
}
```

We'll show a more complicated program where JVerify helps with detecting null dereferences, in the next section, [Class Invariants](class_invariants.md).

