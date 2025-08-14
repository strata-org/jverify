## Reading and Modifying Objects

#### Reads

JVerify guarantees that a `@Pure` method is deterministic, meaning that given the same  it returns the same output. To use this property however, we need to know what the input is. In a language with references like Java, it's not enough to know what the method arguments are.

JVerify requires that a method marked with `@Pure` is explicit about which objects it reads fields from. This can be specified using `reads` calls. The `reads` method takes one or multiple `object` arguments. Example:

```java
class Engine {
    int horsePower;
}
class Car {
    Engine engine;
    
    public int readHorsePower() {
        reads(this, engine); // without this line, the next line would emit an error
        return this.engine.horsePower;
    }
}
``` 

#### Modifies

JVerify requires that methods are explicit about which objects they modify, which can be specified using `modifies` calls. The `modifies` method takes one or multiple objects, and allows the remainder of the calling method to modify these.

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
