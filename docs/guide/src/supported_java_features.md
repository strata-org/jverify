JVerify intends to support the full Java language, but is currently being developed and only supports a subset of Java constructs. The supported and not supported features are listed below. If you encounter unsupported features that are not in the unsupported list, please create a GitHub issue.

# Supported Java features
- All literals
- All operators
  - Equality operators (`==` and `!=`) are not allowed when both operands' types could be String, a record, or a boxed primitive, unless one of the operands is a `null` literal.
    These types are treated as values instead of as references, and accordingly `.equals()` should be used instead.
    For example:
    - If `a` and `b` are statically of type `Object`, then `a == b` is not allowed but `a == null` is allowed.
    - If `i` and `j` are statically of type `Integer` and `k` is of type `int`, then `i == j` is not allowed but `i == 0` and `j == k` are allowed.
- `switch` expressions and statements using switch rules (`case ... -> ...`), although without pattern matching and yield statements
- Field access: `a.x`
- Array access: `a[i]`
- Array types: `int[]`
- Array instantiation: `new []`, although only one-dimensional arrays
- `while` statement
- `for` statement
- The do-while statement
- `if` statement
- Labelled statement: `someLabel:`
- `break`, including with a label
- `continue`, including with a label
- `assert` statement
- Constructors
- `instanceof`, but only without a pattern
- Type casts: `(SomeType)someValue`
- `class`
- `interface`
- `record`, with some restrictions:
  - Explicit constructors and explicit accessors are not allowed
  - Overriding `equals(Object)` or `hashCode()` is not allowed
- `implements`
- `extends`
- Local and anonymous classes
- Lambda expressions
- Method references
- Type parameters, including type parameter bounds
- Type arguments, but wildcards are ignored. `? extends X` is interpreted as `X`, and similarly for `? super X`.

# Supported Java standard library packages

- (Partially) `java.math.BigInteger`

# Not yet supported Java features

#### Java 1-9
- Use of mutating operators such as `=` in a nested expression
- Enhanced for loop: `for(var x : xs) { ... }`
- static fields
- Multi-dimensional array instantiation: `new [,]`
- Array instantiation with initializers: `new int[] { 1, 2, 3 }`
- `throw`/`try`/`catch`/`finally` and checked exceptions
- Operators on `float` and `double`
- Wildcards: `C<? extends X>` is interpreted as `C<X>`, and similarly for `C<? super X>`. `C<?>` is translated to `C<Object>`.
- For now, JVerify can only specify contracts that require none of the arguments, including `this`, are modified concurrently. Support for concurrent modification will be added. When calling verified code from unverified code, be careful not to pass in objects that are being modified concurrently.
  - synchronized is ignored

#### Java 14
- `instanceof <pattern>`, only when using a pattern
- Switch labeled statement groups: `case ...: ...`  (only switch rules `case ... -> ...` are supported)
- `yield` in switch expressions

#### Java 17
- Pattern matches in switch expressions and statements
- `permits` clauses are ignored in verification

#### Java 21
- String template: `STR."My name is \{name}"`