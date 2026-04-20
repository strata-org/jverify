# Supported Java features

JVerify intends to support the full Java language, but is actively under development. It is built on [Strata](https://github.com/strata-org/Strata) and currently supports a limited subset of Java. Support is being expanded incrementally — see issue [#384](https://github.com/strata-org/jverify/issues/384) for the roadmap.

## Currently supported

- **Static methods only.** Instance methods are silently skipped.
- **Primitive integer types:** `byte`, `short`, `int`, `long`.
- **Boolean type.**
- **Arithmetic, comparison, logical operators.**
- **Ternary expressions.**
- **Control flow:** `if`/`else`, `while`, `for`, `return`, `assert`, variable declarations, blocks.
- **JVerify contract primitives:** `precondition()`, `postcondition()`, `check()`, `assume()`, `implies()`, `invariant()`.
- **Quantifiers:** `forall()`, `exists()`.
- **Pure static methods** via `@Pure`.
- **Static method calls.**

## Not yet supported

Major gaps being actively worked on or planned:

- Instance methods, constructors, fields, `this`
- Reference types: classes, interfaces, records
- Arrays
- Generics / type parameters
- `float`, `double`, `char`, `String`
- Boxed primitive types (`Integer`, `Long`, …)
- JVerify annotations: `@Nullable`, `@Unbounded`, `@Nat`, `@Contract`, `@InheritContract`, `@Impure`, `@Invariant`
- `old()` in postconditions
- Inheritance, interfaces, abstract classes
- Lambdas (beyond contract lambdas)
- `break`, `continue`, `switch` statements/expressions, `throw`
- Bitwise and shift operators
- Compound assignment (`+=`), increment/decrement (`++`, `--`)
- Java standard library support (collections, `BigInteger`, streams, optionals)

If you encounter something not in either list, please open a [GitHub issue](https://github.com/strata-org/jverify/issues).

<!--
Previous version of this page (Dafny era). Preserved for reference — lists features
that were unsupported even before the Strata transition.

JVerify intends to support the full Java language, but is currently being developed and only supports a subset of Java constructs. The not yet supported Java features are listed below. If you encounter unsupported features that are not in this list, please create a GitHub issue.

# Currently unsupported Java features

#### Java 1-9
- Enhanced for loop: `for(var x : xs) { ... }`
- Verification of constructors
- static fields
- Multi-dimensional array instantiation: `new [,]`
- Array instantiation with initializers: `new int[] { 1, 2, 3 }`
- `throw`/`try`/`catch`/`finally` and checked exceptions
- Operators on `float` and `double`
- Wildcards: `C<? extends X>` is interpreted as `C<X>`, and similarly for `C<? super X>`. `C<?>` is translated to `C<Object>`.
- Concurrency: for now, JVerify can only specify contracts that require none of the arguments, including `this`, are modified concurrently. Support for concurrent modification will be added. When calling verified code from unverified code, be careful not to pass in objects that are being modified concurrently.
  - The keyword `synchronized` is ignored

#### Java 14
- Deconstructing a record using `instanceof`, for example `shape instanceof Circle(Point(var x, var y), var radius)`
- Switch labeled statement groups: `case ...: ...`  (only switch rules `case ... -> ...` are supported)
- `yield` in switch expressions

#### Java 17
- `permits` clauses are ignored in verification
- Pattern matches in switch expressions and statements
- Explicit constructors on records
- Explicit accessors on records
- Support `null` value and comparison for record types
- Using `==` on records. Note that doing so is generally not what you want.
- Overriding `equals(Object)` or `hashCode()` on records is not supported. Note that doing so is generally not what you want.

#### Java 21
- String template: `STR."My name is \{name}"`

# Supported Java standard library packages

- (Partially) `java.math.BigInteger`
-->