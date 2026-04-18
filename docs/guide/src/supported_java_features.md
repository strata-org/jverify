# Supported Java features

JVerify intends to support the full Java language, but is actively under development. Today it runs on the [Strata](https://github.com/strata-org/Strata) back-end, which supports a limited subset of Java. Support is being expanded incrementally — see issue [#384](https://github.com/strata-org/jverify/issues/384) for the roadmap.

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
