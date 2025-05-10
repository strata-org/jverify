## Modifying fields

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

