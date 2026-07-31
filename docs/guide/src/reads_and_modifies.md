## Reading and Modifying Objects

When defining pure methods, JVerify requires explicitly stating which objects are read by that functions. When defining impure methods, JVerify requires explicitly stating which objects are modified by that method.

Having these two pieces of information allows JVerify to determine which impure method calls can change the result of which pure methods calls, which turns out is often useful when thinking about programs.

#### Reads

JVerify requires that a method marked with `@Pure` is explicit about which objects it reads non-final fields from. This can be specified using `reads` calls. The `reads` method takes one or multiple Object arguments. Example:

```java
class Engine {
    int horsePower;
}
class Car {
    Engine engine;
    
    @Pure
    public int readHorsePower() {
        reads(this, engine); // without this line, the next line would emit an error
        return this.engine.horsePower;
    }
}
``` 

#### Modifies

Methods without `@Pure` should be explicit about which objects they modify, which can be specified using `modifies` calls. The `modifies` method takes one or multiple objects, and allows the remainder of the calling method to modify these.

If you omit `modifies` from a method that does modify the heap, JVerify does not reject it: it infers `modifies` on *everything* instead. That is always sound, but it is coarse — at every call site, callers must assume any field of any object may have changed, so facts they established before the call are lost. Write an explicit `modifies(...)` whenever you want callers to keep that information.

Example:

```java
class Node {
  Node next;
  int value;
  
  void updateValue(int newValue) 
  {
    modifies(this);
    this.value = newValue; // allowed because of the modifies call
  }
  
  void updateNextValue(int newValue) 
  {
    modifies(this.next);
    this.next.value = newValue; // Allowed because next is in the modifies call
  }
}
```

### Newly Allocated Objects

If an object is newly allocated within the body of a method, then the fields of that object may always be modified without needing to be listed in the modifies clause:

```java
Node createAndInitialize(int value)
{
  Node newNode = new Node();
  newNode.value = value; // Allowed because newNode is newly allocated
  return newNode;
}
```

In the next section, [Time traveling](time_traveling.md), we will learn how to write specifications for code that modifies fields.
