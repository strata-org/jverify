# Installation

JVerify can currently only be used by building it from source.

## Prerequisites

1. **Java 23.** If you have a different default Java version installed, pass `-Dorg.gradle.java.home=path-to-java-23` to the gradle commands below.
2. **[Lean 4](https://lean-lang.org/)** (via `elan`) — needed to build the [Strata](https://github.com/strata-org/Strata) verification back-end.
3. **[cvc5](https://cvc5.github.io/)** SMT solver on your `PATH`.

## Building

1. Check out the [JVerify repository](https://github.com/strata-org/jverify) and its submodules:
   ```
   git clone git@github.com:strata-org/jverify.git --recurse-submodules
   ```
2. Navigate to the JVerify repository directory.
3. Build the verifier CLI:
   ```
   ./gradlew installDist     # macOS / Linux
   ./gradlew.bat installDist # Windows
   ```
4. Build the Strata back-end:
   ```
   cd Strata && lake build && cd ..
   ```
5. Point JVerify at the built Strata binary by setting the `JVERIFY_STRATA` environment variable:
   ```
   export JVERIFY_STRATA=$(pwd)/Strata/.lake/build/bin/strata
   ```
   (or pass `--strata /path/to/strata` on every JVerify invocation).
