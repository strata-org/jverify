# Strata Test Triage Results

117 tests total, 68 failed, 49 passed.

## Root cause analysis

### A. Instance methods silently skipped (30 tests)
The Laurel compiler only processes `static` methods. Tests with only instance methods produce zero Laurel procedures, so Strata has nothing to verify and returns empty output. This is the single biggest blocker.

**Fix**: Teach the Laurel compiler to handle instance methods. This requires `this` parameter support at minimum, and full reference type support for methods that use fields/objects.

Tests blocked purely by instance-method-only:
- VerifyBooleanOperators — uses only supported features, would pass if method were static
- VerifyNumericOperators — also needs ++/--, +=
- PrimitiveTypes — also needs @Nat, float/double
- ClassesExtendingClassesErrors — also needs class inheritance
- ConstructorsErrors — also needs constructors, field access
- NullableClassesVerification — also needs @Nullable, reference types
- ImpureExpressionsResolution — also needs ++, +=
- ImpureExpressionsVerification — also needs ++, +=
- DoubleOperators — also needs double, ++, +=
- FloatOperators — also needs float, ++, +=
- ResolutionErrorsBooleanOperators — also needs bitwise operators
- ResolutionErrorsIntegerOperators — also needs bitwise/shift operators
- InterfacesVerification — also needs interfaces, @Contract, field access
- NullableInterfacesVerification — also needs @Nullable, interfaces
- NestedInstanceClass — also needs nested classes, field access, @Nullable
- NestedStaticClass — also needs nested classes, @Nullable
- MultiPackageTest — also needs new objects, cross-file
- NullableRecordsVerification — also needs @Nullable, records, String
- PrimitiveAndBoxedTypesError — also needs Integer boxed type
- CollectionsTest — also needs generics, List/Set/Map
- Optionals — also needs generics, Optional
- Termination — also needs @Nat
- AbstractContractsErrors — also needs isAbstract() API
- AbstractContractsVerification — also needs isAbstract(), class inheritance
- InterfaceContractVerification — also needs @Contract, interfaces
- PureMethodErrors — also needs @Pure validation rules
- PureMethodVerification — also needs @Contract, interfaces
- PureUnverifiedMethod — also needs @Verify(false)
- ShouldVerify — also needs @Verify, new BigInteger()
- VerifyOffByDefault — also needs @Verify

### B. Unsupported type in static method signature/body — compiler crash (25 tests)
The compiler crashes with `JavaViolationException: Unsupported type` when a static method's parameter, return type, or body uses an unsupported type. The crash aborts the entire test class.

**Fix per type**:
- **Reference types/classes** (biggest group): Requires adding class/interface/record type support to `translateType`, plus field access, `new`, `instanceof`, casts in the expression compiler. Deep work.
- **Generics**: Requires type variable support in `translateType` and generic method handling. Depends on reference type support.
- **Arrays**: Requires array type, `JCArrayAccess`, `JCNewArray` in the compiler. Moderate work.
- **float/double**: Requires adding `FLOAT`/`DOUBLE` cases to `translateType` and mapping to Laurel's `Float64Type` or similar. Small work if Laurel supports it.
- **String**: Reference type, plus string operations. Depends on reference type support.
- **Boxed primitives (Integer, etc.)**: Reference types with auto-boxing. Depends on reference type support.
- **JCL types (BigInteger, IntStream, List, Optional)**: Reference types + generics + builtin contracts. Depends on reference type + generics support.

Tests:
- ArraysResolution — `Point[]` in static method
- ArraysVerification — `int[]` in static method
- Allocate — `IntPair` class type
- OverloadsTest — self-referencing class type in method signature
- Operators — `DummyInterface` in method signature
- Doubles — `double` type
- Floats — `float` type
- Lambdas — `JCNewClass` expression in body
- Switches — `JCSwitchExpression` in body
- RecordsDafnyResolutionError — `Object` type
- RecordsErrors — `@Nullable DoorStuck` record type
- RecordsVerified — `UnitRecord` record type
- Strings — `String` type
- PrimitiveAndBoxedTypesVerification — `Integer` type
- MissingContracts — `Integer` type (instance methods only, but crash on class scan)
- TranslationErrors — `@Nullable Integer` type
- WildcardsDafnyResolution — `Container<Object>` generic type
- BigIntegers — `BigInteger` type
- LibraryContractGhostFieldVerification — `BigInteger` type
- AllMatchContract — `IntStream` type
- PolymorphismDafnyResolutionErrors — type variable `T`
- PolymorphismDafnyResolutionErrors2 — `Object` in generic context
- PolymorphismDafnyResolutionErrors3 — `Object` in generic context
- PolymorphismWithBounds — bounded type `Dog`
- PolymorphismWithoutBounds — type variable `T`
- PolymorphicStaticsVerification — type variable `T2`
- UnusedUnverifiedContractRequirement — type variable `E`
- NestedContractsTest — nested interface type

### C. Unsupported JVerify annotations/APIs (8 tests, overlap with A)
Tests use JVerify annotations that the Laurel compiler doesn't process.

**Fix**: Teach the Laurel compiler to handle each annotation:
- `@Nat`, `@Unbounded`: Constrained type annotations on parameters/returns. Moderate — need to generate subset type constraints.
- `@Pure`, `@Erased`: Already partially handled (isPure check exists). Need validation rules.
- `@Verify(false)`, `@Verify(overrideChildren)`: Need to skip/include methods based on annotation. Moderate.
- `@Contract`: External contract classes. Significant — need to resolve and apply contracts from separate classes.
- `isAbstract()`, `preconditionOf()`: Abstract contract APIs. Significant.
- `@Impure`: Impure method marking. Small.

Tests: FibonacciInvalid, InstanceAndStaticInitializerBlock, LibraryContractGhostFieldErrors, InheritedUnverifiedMembers (all also blocked by A or B)

### D. Unsupported operators (5 tests, overlap with A)
- Bitwise: `&`, `|`, `^`, `~` on int/boolean
- Shift: `<<`, `>>`, `>>>`
- Compound assignment: `+=`, `-=`, etc.
- Increment/decrement: `++`, `--`

**Fix**: Add cases to `convertBinary`/`convertUnary` in the Laurel compiler. Small-to-moderate work per operator, if Laurel has corresponding primitives.

### E. `verifyPrintedDafny` flag — Dafny-only test step (5 tests)
These tests have `verifyPrintedDafny = true` in their `@JVerifyTest` annotation. After Strata verification, the test engine tries to re-verify the generated Dafny output by running `backendPath` as a Dafny executable. When the backend is Strata, `backendPath` is a directory, not an executable, causing "Permission denied".

**Fix**: Skip `verifyPrintedDafny` when backend is Strata. Trivial — one `if` check in the test engine. These tests would still fail for other reasons (reference types, generics, lambdas, break/continue).

Tests: AvoidNameCollisionsTest (reference types, lambdas), PolymorphicLambdas (generics, lambdas), PolymorphicAnonymousClasses (generics, anonymous classes), VerifyStatements (break, continue, decreases), MethodContractsVerification (reference types, method references, @Contract)

### F. Partial success — wrong error output (2 tests)
- LocalAndAnonymousClasses — has one static method `staticAdd(int x, int y) { return x + y; }` that compiles and runs through Strata. Strata detects the int32 overflow but reports `Error: assertion does not hold, range=1:1-1:1` instead of `Error: value does not satisfy the subset constraints of 'int32', range=...`. Two issues: wrong error message (should be subset constraint, not assertion) and bogus source location (1:1-1:1). This is a Strata error reporting bug.
- LibraryContractGhostFieldErrors — produces 1 of 3 expected errors. The first error (impure method body check) is produced correctly. The other 2 require @Pure validation rules (uninitialized variable in pure context, pure block must end in return) that the Strata path doesn't implement in the shared frontend.

### G. Crash from nested class scan (1 test)
- MissingContracts — has no static methods in the outer class, but `TreeScanner` recurses into inner classes/records. The `pureUser3()` method returns `Integer` and the `Value` record or `SomethingDoerContract` inner class likely has a synthetic static method with an unsupported type. The crash happens during the recursive scan, not from the outer class's own methods. **Fix**: Catch `JavaViolationException` per method in `visitMethodDef` and skip unsupported methods gracefully instead of crashing the whole class.

---

## Summary: what it takes to fix

| Priority | Fix | Tests unblocked | Effort |
|---|---|---|---|
| 1 | Instance method support | 1 immediately (VerifyBooleanOperators), enables progress on 29 others | Medium |
| 2 | Reference type support (classes, interfaces, new, field access) | ~25 tests | Large |
| 3 | @Verify, @Contract, @Pure validation | ~8 tests | Medium |
| 4 | Generics/type variables | ~6 tests | Large (depends on #2) |
| 5 | Operators (bitwise, shift, ++, +=) | ~5 tests | Small |
| 6 | float/double support | ~4 tests | Small (if Laurel supports it) |
| 7 | Graceful skip of unsupported methods (catch JavaViolationException) | ~1 test directly, prevents crashes across all tests | Trivial |
| 8 | Skip verifyPrintedDafny for Strata backend | 0 (blocked by other issues), but removes noise | Trivial |
| 9 | Arrays | ~2 tests | Medium |
| 10 | switch expressions | ~1 test | Small |
| 11 | @Nat, @Unbounded annotations | ~3 tests | Medium |
| 12 | Strata error message/location accuracy (subset constraints, source ranges) | ~2 tests | Strata-side fix |
