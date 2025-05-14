# Time traveling

JVerify allows evaluating an expression at a point in time different from where the expression occurs. This is most commonly used in postconditions to compare the program state before and after the method executed. 

If an expression `<expr>` is surrounded with a call to `old`, like `old(<expr>)`, it will be evaluated using the program state when the current method was entered. A call to `old` can only be used in erased code, such as a contract or a method annotated with @Erased.

Here's an example:

```java
@Contract
class ListContract<T> implements List<T> {
  @Pure
  public T get(int index) {
    throw new ContractException();
  }
  
  @Pure
  public int size() {
    throw new ContractException();
  }
  
  public boolean add(T value) {
    modifies(this);
    
    // The new size is one larger than the previous one
    postcondition(this.size() == old(this.size()) + 1);
    
    // The last element is the newly added one
    postcondition(this.get(size() - 1) == value);
    
    // All elements except the added one have remained the same
    postcondition(forall((Integer i) -> implies(
      0 <= i && i < old(this.size()), 
      this.get(i) == old(this.get(i))
      ))); 
  }
}
```