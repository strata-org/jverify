# JVerify sample project

A small Gradle project that compiles against the JVerify library and verifies one class.

- `app/src/main/java/org/example/App.java` has one method with a contract, `max`.
- `app/src/main/java/org/example/Main.java` holds `main`. It is kept in its own file, which is not passed to JVerify, because JVerify cannot verify a `main(String[] args)` method yet.
- `app/build.gradle.kts` defines the `verify` task. It takes the JVerify library jar and the verifier from `../bytecode-frontend/build/install/bytecode-verifier` (relative to this directory), where the JVerify build installs them.

## Before you start

1. Build the verifier and put Java 25 and the Strata back-end on the `PATH` of this shell, as described in [Installation](../docs/guide/src/installation.md), steps 1 to 3.
2. Check that the first line of `java -version` shows version 25:

   ```
   $ java -version
   openjdk version "25.0.5" 2026-10-20 LTS
   ```

   The two lines it prints after that name the vendor and the build of the JDK; they vary and do not matter.

The Gradle wrapper downloads Gradle 9.4.0 and JUnit the first time, so the first build needs network access.

## Commands

Run these in `sample-project/`.

Compile the project and run its unit test:

```
./gradlew build
```

Run the program, which prints `7`:

```
./gradlew run
```

Verify `App.java`:

```
./gradlew verify
```

```
> Task :app:verify
Found 0 errors
Verified methods: 2
Failed methods: 0
Skipped methods: 0
```

The two verified methods are `max` and the default constructor that the Java compiler adds to `App`, because `App` declares none. No method failed and none was skipped, so this is a full pass.

## See a failure

In `App.java`, change `return b;` to `return b + 1;` and run `./gradlew verify` again:

```
> Task :app:verify FAILED
App.java(7:34-13:6): Error: postcondition does not hold
App.java(8:1-8:84): Error: postcondition does not hold
App.java(12:9-12:22): Error: no int overflow on '+' does not hold
App.java(12:9-12:22): Error: assertion does not hold
Found 4 errors
Verified methods: 1
Failed methods: 1
Skipped methods: 0
```

JVerify exits with code 4, so Gradle marks the task failed and exits with code 1.

The change broke two things, and JVerify reports each of them twice:

- The first two lines are the postcondition, which `b + 1` does not satisfy. The first position, `(7:34-13:6)`, is the whole body of `max`; the second is the `postcondition` line.
- The last two lines are the addition on line 12, which overflows when `b` is the largest `int`. JVerify reports a failed overflow check once under its own name and once as `assertion`, the word it prints for a check that has no more specific name.

So go by the distinct positions and by `Failed methods`, not by the number in `Found 4 errors`.

## If something goes wrong

| What you see | What to do |
|---|---|
| `ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.`, followed by two lines about `JAVA_HOME`, from any `./gradlew` command | This shell has no Java on `PATH`. Run the first `export` of [Installation](../docs/guide/src/installation.md) step 3 from the JVerify package directory. |
| `Cannot find a Java installation on your machine (...) matching: {languageVersion=25, ...}` from any `./gradlew` command | Gradle was started with a Java other than 25 and found no Java 25 on this machine. Put Java 25 first on `PATH`, as above. The lines `[Incubating] Problems report is available at: ...` and `Deprecated Gradle features were used in this build, ...` that Gradle prints around this message are not part of the problem. |
| `error: package org.strata.jverify does not exist` from `./gradlew build` | The JVerify library jar is missing. Build the verifier first (step 1 above). |
| `A problem occurred starting process` from `./gradlew verify` | The verifier is missing. Build it first (step 1 above). |
| ``Failed to run `strataLaurel` from PATH`` from `./gradlew verify` | The Strata back-end is not on the `PATH` of this shell. Run the second `export` of [Installation](../docs/guide/src/installation.md) step 3 from the JVerify package directory. |
