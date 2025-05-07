# Integers

## Nat

// The annotation @Nat indicates that the int must be non-negative

@Nat, which states that the following number may not be negative.  @Nat is part of a general class of annotations that can be used with JVerify called subset types, and we will look at how to define them in the next section.

## Unbounded numbers

While Java’s `int` is a bounded number, JVerify allows reasoning about unbounded numbers using the existing `int` type, by prefixing it with the `@Unbounded` annotation. Reasoning about unbounded numbers is usually simpler than with bounded ones. However, `@Unbounded` can only be used on erased code.

Here follows an example that uses `@Unbounded`.

```java
class Fibonacci {
  @Pure
  @Erased 
  static @Unbounded @Nat int Spec(@Unbounded @Nat int n) {
    return n < 2 ? n : Spec(n - 1) + Spec(n - 2);
  }
  
  public static @Nat int Implementation(@Nat int n)
  {
    postcondition((Integer r) -> r == Spec(n));
    
    precondition(Spec(n) < Integer.MAX_VALUE); 
    // this precondition prevents overflow later in this method
    
    if (n == 0) {
      return 0;
    }

    int previousResult = 0;
    int result = 1;
    int i = 1;
    while(i < n)
    {
      invariant(result == Spec(i));
      invariant(previousResult == Spec(i - 1));
      invariant(i <= n);
      
      i = i + 1;
      
      int addition = result + previousResult;
      // Without the precondition, 
      // JVerify would detect that the previous statement can overflow
        
      previousResult = result;
      result = addition;
    }
    return result;
  }
}
```