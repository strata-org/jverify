# Integers

## Bounded numbers
The types `int`, `short` and `long` in Java are bounded. The values of these numbers all have lower and upper bounds, and doing arithmetic operations that cause these numbers to surpass these bounds, called an overflow, generally causes undesired results.

JVerify will check that any arithmetic operations on bounded number will not overflow. Example:

```java
int triple(int x) {
  return 3 * x; // error: returned value may not be within the bounds of 'int'    
} 
```

You can let JVerify know no such overflow occurs by bounding the provided values:
```java
int triple(int x) {
  precondition(3 * x < Integer.MAX_VALUE);
  
  return 3 * x; // pass    
} 
```

## Unbounded numbers
Using the annotation `@Unbounded`, you can annotate integer types so JVerify will allow them to have unbounded values. Example:
```java
@Erased
@Unbounded int triple(int x) {
  return 3 * x; // pass    
}
```

The `@Unbounded` annotation can only be used in erased code.

## Nat

The annotation `@Nat` can be used to tell JVerify that an integer is never negative. Example:

```java
int factorial(int n) {
    if (n == 0) {
        return 1;
    } else {
        return factorial(n - 1); // error: could not prove termination
    }
}
```

JVerify can not prove that the recursive call terminates, because if you pass in a negative number, then it won't. We can solve this by using `@Nat`:

```java
int factorial(@Nat int n) {
    if (n == 0) {
        return 1;
    } else {
        return factorial(n - 1); // pass
    }
}
```

Instead of `@Nat`, we could also have used `precondition(n >= 0)`, but that is more verbose.

## Combining the above
Here follows an example that uses unbounded numbers is its specification, bounded numbers in the implementation, and natural numbers in both. It shows how each of these concepts is useful:

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