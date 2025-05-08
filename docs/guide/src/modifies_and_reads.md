# Modifies and Reads

JVerify allows you to precisely specify which parts of memory a method can read from and modify. These specifications help JVerify reason about your code and prove properties that would otherwise be difficult or impossible to verify.

## Modifies Clauses

By default, methods in JVerify are allowed to read any memory they like, but they are required to list which parts of memory they modify using a `modifies` clause. This helps JVerify track and verify state changes throughout your program.

### Basic Usage

```java
class Node {
  Node next;
  int value;
  
  void updateValue(int newValue) 
    modifies(this)
  {
    this.value = newValue;
  }
  
  void updateNextValue(int newValue) 
    modifies(this.next)
  {
    this.next.value = newValue; // Allowed because next is in the modifies clause
  }
}
```

### Specifying Multiple Objects

You can specify multiple objects in a modifies clause:

```java
void updateBoth(Node a, Node b, int newValue)
  modifies(a, b)
{
  a.value = newValue;
  b.value = newValue;
}
```

### Field-Specific Modifications

You can also specify that only specific fields of an object may be modified:

```java
void updateOnlyValue(Node node)
  modifies(node`value)
{
  node.value = 42; // Allowed
  // node.next = null; // This would cause a verification error
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

### Important Notes

1. The `modifies` clause specifies what *may* be modified, not what *must* be modified.
2. Writing the same value to a field still counts as a modification, even if the value doesn't change.
3. Even if you restore a field to its original value by the end of a method, you still need to list it in the modifies clause.

## Reads Clauses

While methods can read any memory by default, `@Pure` methods and functions must explicitly declare what memory locations they depend on using `reads` clauses.

### Basic Usage

```java
@Pure
int getValueSum(Node node)
  reads(node)
{
  return node.value + (node.next != null ? node.next.value : 0);
}
```

### Transitive Reading

When you specify an object in a reads clause, you're allowed to read all fields of that object:

```java
@Pure
int countNodes(Node head)
  reads(head)
{
  int count = 0;
  Node current = head;
  
  while (current != null) {
    count++;
    current = current.next;
  }
  
  return count;
}
```

In this example, we need to include `head` in the reads clause to be able to access `head.next` and traverse the linked list.

### Field-Specific Reading

Similar to modifies clauses, you can specify that only specific fields may be read:

```java
@Pure
int getValueOnly(Node node)
  reads(node`value)
{
  return node.value;
  // Accessing node.next would cause a verification error
}
```

## Combining Reads and Modifies

Methods that both read and modify state should include both clauses:

```java
void incrementAllValues(Node head)
  reads(head)
  modifies(head)
{
  Node current = head;
  while (current != null) {
    current.value++;
    current = current.next;
  }
}
```

## Benefits

Using reads and modifies clauses provides several benefits:

1. **Modularity**: JVerify can reason about one method at a time
2. **Predictability**: Clear documentation of a method's side effects
3. **Verification**: Enables proving properties about code that would otherwise be difficult
4. **Bug Prevention**: Catches unintended modifications to objects
