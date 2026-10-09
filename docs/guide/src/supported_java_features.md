# Supported Java features

JVerify intends to support the full Java language, but is actively under development. It is built on [Strata](https://github.com/strata-org/Strata) and currently supports a limited subset of Java. Support is being expanded incrementally — see issue [#384](https://github.com/strata-org/jverify/issues/384) for the roadmap.

## Currently supported

- **Static and instance methods.** Instance methods are translated, including calls
  through a receiver. A method that cannot be translated is skipped with a warning
  naming the construct responsible, not silently.
- **Primitive integer types:** `byte`, `short`, `int`, `long`.
- **Boolean type.**
- **Arithmetic, comparison, logical operators.**
- **Ternary expressions.**
- **Control flow:** `if`/`else`, `while`, `for`, `return`, `assert`, variable declarations, blocks.
- **JVerify contract primitives:** `precondition()`, `postcondition()`, `check()`, `assume()`, `implies()`, `invariant()`.
- **Quantifiers:** `forall()`, `exists()`.
- **Pure static methods** via `@Pure`.
- **Static method calls.**
- **Exceptions:** `throws` clauses (unchecked types included; multi-`throws`
  joins to the closest common ancestor), `throw`, `try`/`catch`/`finally`
  (multi-catch included), and exceptional behavior cases via
  `throwsOn(...).postcondition(...).modifies(...)` — see the Exceptions
  chapter. Not yet: try-with-resources, exceptions carrying constructor
  data.
- **Classes and interfaces**, with dynamic dispatch on the receiver's runtime type.
  An override must refine what it overrides, and JVerify checks that obligation, so a
  caller may reason from the static type's contract.
- **Generics:** a method's own type parameters and a class's. A type parameter carries
  no bound into the translation, so a body that uses the bound's members is still
  refused, and `==` on a type parameter is restricted as it is on `Object`.
- **Method overloading**, including overloads split across an inheritance chain.
- **Reference operations:** upcasts, downcasts, and `instanceof` on types declared in
  the code under verification.
- **Compound assignment** (`+=`, `-=`, `*=`, `/=`, `%=`) on a local or parameter.
  See the caveat below on narrowing.
- **`switch` expressions** in arrow form (`case 0 -> 1`). Not yet: the
  statement-group form (`case 0:` with `yield`), a block or `throw` as an arm body,
  and `case` patterns.
- **Annotations:** `@Pure`, `@Impure`, `@Verify`, `@Contract`, `@Unbounded`, `@Nat`.

## Not yet supported

Major gaps being actively worked on or planned:

- **Constructors.** A constructor is not translated and a `new` expression discards its
  arguments, so an object's fields are unconstrained immediately after construction.
  This is the single biggest gap: any assertion about a field of a freshly constructed
  object fails, even when the constructor's own postcondition is provable.
- `float` and `double` — a floating-point literal is rejected outright.
- Arrays
- Records, beyond being treated as values: a component accessor is not emitted.
- Boxed primitive types (`Integer`, `Long`, …) beyond unboxing against a primitive
- `old()` in postconditions
- `@Nullable` (no null representation yet), `@InheritContract`, `@Invariant`
- Lambdas beyond contract lambdas and a functional interface implemented by one
- `break`, `continue`, `switch` *statements*
- Increment/decrement (`++`, `--`)
- A narrowing compound assignment such as `byte b = 100; b += 100;` reports an
  assertion failure rather than Java's wrap-around result — the narrowing is not
  modelled, by design, and the diagnostic is currently misleading.
- Java standard library support (collections, `BigInteger`, streams, optionals). The
  shipped contracts cover some `java.lang` types; the generic collection contracts do
  not yet resolve.

If you encounter something not in either list, please open a [GitHub issue](https://github.com/strata-org/jverify/issues).

### Rejected up front, rather than skipped

Some unsupported constructs are reported as an **error** when JVerify reads your
source, rather than as a per-method "was not verified" warning. An error means a
non-zero exit, so a project that previously built green while quietly skipping the
affected methods will now fail. Rejecting up front is deliberate: a skipped method
looks verified at a glance, and these constructs cannot be checked at all.

The constructs rejected this way are:

- Bitwise and shift operators (`&`, `|`, `^`, `~`, `<<`, `>>`, `>>>`)
- `%` on `float` or `double`
- `==` and `!=` on references where neither operand's type is impure — including boxed
  comparisons such as `Integer == Integer`
- Instance and static initializer blocks
- `isAbstract()` anywhere but a precondition
- A contract call (`precondition`, `postcondition`, …) placed after the body, in a
  loop, or otherwise out of position
- A `@Pure` method whose body is not reducible to an expression — it must end in a
  `return` (or an `if`/`else` that does), and may otherwise contain only variable
  declarations, each with an initializer, and no mutation
- A malformed `@Contract` class
- Any construct that cannot be translated in a **static** method: a static method that
  cannot be translated is an error, an instance method a warning

To keep building while one of these is present, opt the code out of verification
with `@Verify(false)`. It is honored on the method and on any enclosing class.

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