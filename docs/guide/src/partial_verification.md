# Partial Verification

Given a codebase, you can configure what methods to verify. You can toggle verification on or off at the various levels in your code's hierarchy: `CLI`, `package`, `type`, `method`.

The CLI option `--verify-by-default` determines whether methods are verified by default or not.

The annotation `@Verify` can be used to turn verification on or off at various levels. Here's an example:

```java
@Verify // turn on verification inside this class
class C {
    @Nullabe Integer i;
    
    void foo() {
        var x = 0;
        if (i != null) {
            x += i;
            fooCallee();
            x += 2 * i; 
//                   ^ error: could not prove `i` is not null. 
        }
    }

    @Verify(false) // turn off verification for this method
    void fooCallee() 
    {
        modifies(this);
        i = null;
        int x = i; // no error, because fooCallee is not verified
    }
}
```

You can enforce an entire hierarchy level to be verified by using the `overrideChildren` argument of `@Verify`. The following code forces any code in the package `my.safe.code` to be verified:

```java
@Verify(overrideChildren = true)
package my.safe.code;
```

### Preconditions of callees

When calling a method from verified code, to ensure the caller's code is correct, the callee must always have a precondition, although it may be empty. Here's an example:

```java
@Verify(false)
class C {
    @Verify
    void foo() {
        bar(); // no error
        zaz(); // error, no precondition was specified for `barCallee`
        zoo(); // no error
    }

    @Verify // because this method is verified
    // we can assume an empty precondition
    // if none is specified
    void bar() {
        // body
    }
    
    // Because this method is not verified,
    // we can not assume an empty precondition,
    // so this method missing a precondition
    void zaz() {
        // body
    }
    
    void zoo() {
        precondition(); // we use an empty precondition
        // to give this method a precondition
    }
}
```

