# Running JVerify

This chapter verifies a small program, explains the output, and then breaks the program to show what a failure looks like. It assumes that you built the verifier and put Java and the Strata back-end on your `PATH`, as described in [Installation](./installation.md), and that your shell is in the package directory, the one that contains `examples` and `bytecode-frontend`.

## The example

The program is `examples/src/test/java/org/strata/jverify/examples/GaussianSum.java`:

```java
{{#include ../../../examples/src/test/java/org/strata/jverify/examples/GaussianSum.java}}
```

`sumTo(n)` adds up `0 + 1 + ... + n` in a loop. The calls to `precondition`, `postcondition` and `invariant` are its *contract*: statements about what the code requires and what it guarantees, written as ordinary Java calls.

- `precondition(...)` states what the method requires of its inputs. Here `n` must be between 0 and 46340.
- `postcondition(...)` states what the method guarantees about its result. Here the result equals `n * (n + 1) / 2`.
- `invariant(...)` states a fact that is true each time the loop condition is tested. JVerify needs invariants to reason about a loop; [How the proof works](#how-the-proof-works) explains them.

JVerify proves the postcondition for every `n` the precondition allows, without running the code. To do so it splits the method into *checks*. A check is one fact to prove at one place in the code: that the postcondition holds at a `return`, that an invariant holds when the loop is first reached, or that a `+` does not overflow. A method is verified when all its checks are proved.

The bound `n <= 46340` keeps `n * (n + 1)` inside `int`. JVerify checks every `+`, `-` and `*` for overflow, and 46340 * 46341 still fits in an `int` while 46341 * 46342 does not.

## Run it

```
./bytecode-frontend/build/install/bytecode-verifier/bin/bytecode-verifier ./examples/src/test/java/org/strata/jverify/examples/GaussianSum.java
```

After about two seconds JVerify prints:

```
Found 0 errors
Verified methods: 2
Failed methods: 0
Skipped methods: 0
```

If you see something else, for example ``Failed to run `strataLaurel` from PATH``, the table at the end of [Installation](./installation.md) says what it means and what to do.

## Read the result

Every method of the files you passed is counted in exactly one of three counters:

- **Verified methods**: every check in the method was proved. The count is 2 for a class with one method because the Java compiler adds a default constructor to a class that declares none, here `GaussianSum()`, and JVerify verifies it too.
- **Failed methods**: at least one check in the method was not proved. Each such check is listed above the counters.
- **Skipped methods**: the method was not checked at all. There are two reasons for a skip. Either you opted the method out with the annotation `@Verify(false)`, which tells JVerify not to check it ([Verify your own code](#verify-your-own-code) shows how). Or the method uses a Java feature that JVerify does not support yet.

**A run is a full pass when it prints `Failed methods: 0` and every skipped method is one you opted out yourself with `@Verify(false)`.** The example opts nothing out, so its pass shows `Skipped methods: 0`.

A skip of the second kind is easy to miss, because the run still prints `Found 0 errors` and exits with code 0. `SimpleNullCheck.java`, in the same directory as the example, shows one:

```
./bytecode-frontend/build/install/bytecode-verifier/bin/bytecode-verifier ./examples/src/test/java/org/strata/jverify/examples/SimpleNullCheck.java
```

```
SimpleNullCheck.java(14:21-14:22): warning: instance method 'useFooer' was not verified: Unsupported expression: NullLiteralExpr
SimpleNullCheck.java(26:18-26:19): warning: instance method 'clear' was not verified: Unsupported expression: AnonymousDynamicConstantDesc
Found 0 errors
Verified methods: 3
Failed methods: 0
Skipped methods: 2
```

Each warning names a method that JVerify did not check. Nothing was proved about `useFooer` or `clear`, so this run is not a full pass. The words that matter are `was not verified`. What follows them, such as `NullLiteralExpr`, is JVerify's internal name for the part of the method it could not translate; you do not need to understand it. [Supported Java features](./supported_java_features.md) lists what JVerify can check today.

The exit code summarizes the run for scripts:

| Exit code | Meaning |
|---|---|
| 0 | No check failed. This alone is not a full pass, because methods may have been skipped. |
| 1 | Java could not start JVerify: `java` is missing or older than 25. |
| 2 | The command line was wrong, or the file could not be translated, for example because it does not compile. |
| 4 | At least one check was not proved. This includes a check on which the solver crashed or ran out of time. |
| 255 | The back-end could not be started. |

## Break it

The next steps edit the example file, `examples/src/test/java/org/strata/jverify/examples/GaussianSum.java`. To undo all your edits at any time, run:

```
git checkout -- examples/src/test/java/org/strata/jverify/examples/GaussianSum.java
```

In that file, change `return s;` to `return s + 1;` and run the same command as in [Run it](#run-it):

```
GaussianSum.java(16:1-16:60): Error: postcondition could not be proved
Found 1 errors
Verified methods: 1
Failed methods: 1
Skipped methods: 0
```

The exit code is 4. `sumTo` moved from verified to failed; the method still counted as verified is the constructor. The position `(16:1-16:60)` reads start line, start column, end line, end column. Line 16 is the `postcondition` line, so the check that failed is the postcondition.

Put `return s;` back, then raise the bound to `n <= 46341` and run again:

```
GaussianSum.java(16:36-16:58): Error: no int overflow on '*' does not hold
GaussianSum.java(21:13-21:45): Error: no int overflow on '*' does not hold
Found 2 errors
Verified methods: 1
Failed methods: 1
Skipped methods: 0
```

For `n == 46341` the products `n * (n + 1)` on line 16 and `i * (i + 1)` on line 21 overflow. JVerify checks the arithmetic in contracts as well as in code.

The two runs word the failure differently. `does not hold` means the SMT solver found inputs for which the check is false. `could not be proved` means it found neither a proof nor such inputs. With `return s + 1;` the postcondition is false for every `n`, and the message was still `could not be proved`: the solver sees the loop only through its invariants, as the next section explains, and then it does not always find concrete inputs. Either way the method is not verified.

Restore `n <= 46340` before you go on.

## How the proof works

Each line of the contract has a job. If you remove either bound of the precondition or either invariant, the run fails with exit code 4.

- `precondition(0 <= n && n <= 46340)` says which inputs the method accepts. JVerify assumes it inside `sumTo` and requires every caller to establish it. The lower bound is needed because for a negative `n` such as -2 the loop does not run and the method returns 0, while `n * (n + 1) / 2` is 1. The upper bound rules out overflow.
- `postcondition((int res) -> res == n * (n + 1) / 2)` says what the method guarantees. `res` names the returned value, and JVerify checks the postcondition at every `return`.
- JVerify does not run or unroll the loop. All it knows after the loop is that the invariants hold and the loop condition is false, so the invariants have to carry everything the postcondition needs. JVerify checks each invariant when the loop is first reached and again after every iteration.
  - `invariant(0 <= i && i <= n)` gives the range of the counter. Together with the exit condition `!(i < n)` it gives `i == n` after the loop, and it keeps `i + 1` and `i * (i + 1)` in range.
  - `invariant(s == i * (i + 1) / 2)` says that `s` is the sum of the numbers handled so far. With `i == n` it becomes the postcondition.

Removing a fact does not always give one tidy error line. With the lower bound `0 <= n` removed from the precondition, the run prints:

```
Note: 1 error(s) could not be attributed to a specific method; the method counts below do not reflect them.
GaussianSum.java(16:36-16:58): Error: analysis error: Solver Timeout! stderr:
strata-unattributed(1:1-1:1): Error: malformed diagnostic from Strata: solver stdout: timeout
GaussianSum.java(20:13-20:41): Error: assertion does not hold
Found 3 errors
Verified methods: 1
Failed methods: 1
Skipped methods: 0
```

This is a failed proof, not a broken installation:

- `Solver Timeout!` means the solver ran out of time on a check, here the one on line 16. The `strata-unattributed ... malformed diagnostic` line after it belongs to the same timeout.
- `assertion does not hold` points at the first invariant, on line 20. For a negative `n` it is false when the loop is first reached. `assertion` is the word JVerify prints for a check that has no more specific name.
- The `Note` line says that JVerify could not tell which method some of the errors belong to. The three counters then count too few failures, so go by the error lines.

Removing `invariant(0 <= i && i <= n)` instead gives ten error lines, one of them another `Solver Timeout!`. Restore the file before you go on.

[Pre- and post-conditions](./pre_and_postconditions.md), [Loop Invariants](./loop_invariants.md) and [Integers](./integers.md) cover these in depth.

## Verify your own code

Pass any Java file to JVerify:

```
./bytecode-frontend/build/install/bytecode-verifier/bin/bytecode-verifier path/to/YourFile.java
```

JVerify compiles the file itself, with the JVerify library on the compile classpath. The contract methods therefore need one import and no build setup:

```java
import static org.strata.jverify.JVerify.*;
```

If your file depends on other jars, add `--classpath your.jar` after the file names. Options that take a list, such as `--classpath`, must come after the files.

JVerify cannot verify a `public static void main(String[] args)` method yet, and it rejects a file that contains one. To see this, add a `main` method to `GaussianSum.java`. Insert these three lines directly after the `}` that closes `sumTo`, with no blank line before `public`:

```java
    public static void main(String[] args) {
        System.out.println(sumTo(10));
    }
```

The run stops with exit code 2:

```
GaussianSum.java(27:44-27:45): error: arrays of reference types are not yet supported
Found 1 errors
```

The array that the message means is the parameter `String[] args`. The position `(27:44-27:45)` is the `{` that opens the body of `main`; with a blank line before `public` it is `(28:44-28:45)`.

There are two ways out. Either keep `main` in a file that you do not pass to JVerify, or opt `main` out with `@Verify(false)`. The annotation needs its own import, below the existing one:

```java
import org.strata.jverify.Verify;
```

and goes on the line above the method:

```java
    @Verify(false)
    public static void main(String[] args) {
```

JVerify then counts `main` under `Skipped methods`, without a warning:

```
Found 0 errors
Verified methods: 2
Failed methods: 0
Skipped methods: 1
```

The one skipped method is `main`, which you opted out, so this run is a full pass. Undo your edits with the `git checkout` command from [Break it](#break-it).

The directory `sample-project/` is a small Gradle project that uses JVerify. It compiles against the JVerify library, keeps `main` in its own file, and has a `verify` task. Its `README.md` lists the commands.

## Current limitations

JVerify currently supports a limited subset of Java. [Supported Java features](./supported_java_features.md) lists what it can check today.
