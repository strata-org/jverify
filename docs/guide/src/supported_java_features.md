JVerify intends to support the full Java language, but is currently being developed and only supports a subset of Java constructs. The supported and not supported features are listed below. If you encounter unsupported features that are not in the unsupported list, please create a GitHub issue.

# Supported Java Features
- All literals except strings
- All operators
- `switch` expressions and statements, although without pattern matching and yield statements
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
- `class`, but only without type parameters
- `interface`, but only without type parameters
- `implements`, but only classes implementing interfaces
- `extends`, but only interfaces extending interfaces

# Supported Java standard library packages

Nothing yet

# Unsupported Java features

- Pattern matches in switch expressions
- `switch` statement
- `yield` in switch expressions
- Lambda expressions
- Method references
- `import` statements
- `package` statements
- Multi-dimensional array instantiation: `new [,]`
- Array instantiation with initializers: `new int[] { 1, 2, 3 }`
- Type casts: `(SomeType)someValue`
- Type parameters for types, including the declaration, declaration of bounds, and application
- `extends`: but only classes inheriting other classes
- Enhanced for loop: `for(var x : xs) { ... }`
- Use of mutating operators such as `=` in a nested expression
- Operators on `float` and `double`
- `instanceof <pattern>`, only when using a pattern
- String literal: `"hello"`
- String template: `STR."My name is \{name}"`
- `throw`
- `try`/`catch`/`finally`
- `checked exceptions` (ignored)
- `synchronized`
- Module declarations
- Wildcards: `?`
- `permits`
- Verification of annotations
- For now, JVerify can only specify contracts that require none of the arguments are modified concurrently. Support for concurrent modification will be added. When calling verified code from unverified code, be careful not to pass in objects that are being modified concurrently.

[//]: # (# Do not need to mention)

[//]: # ()
[//]: # (- Method invocation)

[//]: # (- Variable declaration)

[//]: # (- Expression statement)

[//]: # (- Return)

[//]: # (- Blocks `{}`)

[//]: # (- Default case label)

[//]: # (- Skip)

[//]: # (- LetExpr, not part of grammar)

[//]: # (- Type intersection, not part of grammar)

[//]: # (- Type union, not part of grammar)