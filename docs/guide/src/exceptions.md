# Exceptions

JVerify verifies code that throws, catches, and declares exceptions, and lets
you specify *exceptional behavior*: when a method throws, what it throws, and
what may change when it does.

## Throwing and declaring

A method that throws declares the thrown type in its `throws` clause. This is
Java's own rule for checked exceptions; JVerify applies it to unchecked ones
too, because the declaration is what a caller's proof hangs on:

```java
class Parser {
    static class ParseException extends Exception {}

    static int parse(int input) throws ParseException {
        if (input < 0) {
            throw new ParseException();
        }
        return input;
    }
}
```

From the declaration alone, every caller knows the only type this method can
throw is `ParseException` — that is checked against the body, not assumed.
A `throw` without a covering `throws` clause (or enclosing `catch`) is
rejected, as is a thrown type outside the declared one.

Declaring several types (`throws ParseException, IOException`) is supported;
internally they join to their closest common ancestor, and per-type claims
are recovered in case postconditions with `instanceof` (see below).

## Catching

`try`/`catch`/`finally` behave as in Java: arms are tried in order, the first
whose type matches the thrown value runs, and `finally` runs on every path
out — normal, throwing, or a `return`/`break` unwinding through it.
Multi-catch (`catch (A | B e)`) and re-throwing from an arm work too.

```java
static int callAndDefault(int input) {
    try {
        return Parser.parse(input);
    } catch (Parser.ParseException e) {
        return 0;
    }
}
```

The handler above is not dead code as far as the caller can tell — but if it
*can* prove the call never throws (see exceptional behavior cases below), it
can also prove the handler never runs.

## Exceptional behavior cases

The contract vocabulary gains three markers, used in the contract header like
`precondition` and `postcondition` (normal-exit clauses first, then the
exceptional cases):

```java
static class IndexError extends Exception {}

static int get(int i, int len) throws IndexError {
    precondition(len >= 0);
    throwsOn(i < 0 || i >= len);
    if (i < 0 || i >= len) {
        throw new IndexError();
    }
    return i;
}
```

- `throwsOn(C)` opens an *exceptional behavior case*: when `C` holds on
  entry, the method **must** throw. This is a two-way contract. The body is
  checked to throw whenever `C` holds, and a caller that establishes `C`
  knows the call throws — so code after it on the normal path is dead.
- The case's clauses chain off the `throwsOn(...)` call, reusing the words
  you already know, scoped to that throwing path:
  `.postcondition((E e) -> P)` states that `P` holds of the thrown value
  (the parameter is typed at the declared throws type; narrow to a subtype
  inside the property, e.g.
  `.postcondition((AppException e) -> implies(e instanceof ParseError, ((ParseError) e).code > 0))`),
  and `.modifies(x)` is the case's frame: on that throwing path, only the
  named objects may change — mirroring the plain `modifies`, which frames
  the normal path only. A clause cannot be written without its case: the
  clause methods only exist on the value `throwsOn` returns.

Stating any case is a claim to have enumerated them all: JVerify checks that
every throwing path satisfies some case's condition. Two consequences worth
knowing:

- `throwsOn(false)` states the method never throws — and is checked. A
  caller can then treat any handler around the call as dead.
- Stating *no* case is different: it leaves the throwing paths unconstrained
  (only the declared type is known), which is also the only way to leave
  them unconstrained.

A caller that refutes every case condition proves the call cannot throw:

```java
static int handlerIsDead(int i, int len) {
    int safe = i < 0 ? 0 : (i >= len ? 0 : i);
    try {
        return get(safe, len);   // every throwsOn condition refuted
    } catch (IndexError e) {
        check(false);            // provably unreachable
        return -1;
    }
}
```

## Implicit runtime exceptions

Null dereferences, division by zero, out-of-bounds accesses, and bad casts
are *proof obligations*, not catchable events: JVerify requires you to prove
they cannot happen. Consequently a
`catch (NullPointerException e)` around verified code is dead by
construction. Explicitly *throwing* an unchecked exception is fine — declare
it in the `throws` clause like any other.

The bytecode frontend treats integer division by zero (`ArithmeticException`)
and a failing reference downcast (`ClassCastException`) as real exceptions
instead: an uncaught one is an error only if it can actually be thrown, and a
caught one is ordinary control flow, so code that handles it verifies. A
`throw` of a program-defined unchecked exception there needs no `throws`
clause either; it is reported only if it is reachable and not caught (throwing
a library exception such as `IllegalArgumentException` is not supported yet).
Null dereferences are not modelled, since the bytecode frontend does not
support `null`, and an array index out of bounds is still a precondition of
the access rather than a thrown `ArrayIndexOutOfBoundsException`.

## Deliberate strictness

Two deliberate strictness choices, both consequences of the forcing
semantics:

- There is no unforced "if it throws, then P" clause: a case's condition
  always *forces* its throw. Forcing contracts are strictly stronger; this is
  what makes handler-dead and cannot-throw proofs possible.
- Stating any case claims the enumeration of throwing conditions is
  complete.

## Current limitations

- Exception constructors: `new E(args)` does not yet carry constructor
  arguments into the model; give the exception fields and assign them after
  construction, or wait for constructor support.
- A throw as a switch *expression* rule body is not yet supported (statement
  switches are fine).
- Interpret mode does not run exception tests (thrown values live on the
  heap, which the interpreter does not model yet).
