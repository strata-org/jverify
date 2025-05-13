# Unsupported Java features

- `switch` statement
- `switch` expression
- `yield` in switch expressions
- record pattern in switch expression
- lambda expressions
- method references
- import statements
- package statements
- Multi-dimensional array instantiation: `new [,]`
- Array instantiation with initializers: `new int[] { 1, 2, 3 }`
- type casts: `(SomeType)someValue`
- type parameters for types, including the declaration, declaration of bounds, and application
- `extends`: but only classes inheriting other classes
- enhanced for loop: `for(var x : xs) { ... }`
- use of mutating operators such as `=` in a nested expression
- operators on `float` and `double`
- `instanceof <pattern>`, only when using a pattern
- string literal: `"hello"`
- string templats: `STR."My name is \{name}"`
- `throw`
- `try`/`catch`/`finally`
- `checked exceptions` (ignored)
- `synchronized`
- module declarations
- `?` wildcards
- `permits`
- verification of annotations
- Any classes that rely on concurrent analysis currently can not be analyzed by JVerify

# Supported Java standard library packages

Nothing yet

# Supported Java Features

- all literals except strings
- all operators
- Field access: `a.x`
- Array access: `a[i]`
- Array types: `int[]`
- Array instantiation: `new []`
- `while` loop
- `for loop`
- `do while loop`
- `if` statement
- Labelled statement: `someLabel:`
- `break`, including with a label
- `continue`, including with a label
- `assert` statement
- constructors (partially?)
- `instanceof` without a pattern
- `class`, partially
- `interface`
- `implements`, classes implementing interfaces
- `extends`, interfaces extending interfaces

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