# Running JVerify

Let's try running JVerify on the following Java program, found [here](https://github.com/strata-org/jverify/blob/main/examples/src/test/java/com/aws/jverify/examples/Postconditions.java):

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/Postconditions.java}}
```

To run JVerify, from the repository root:

```
./verifier/build/install/verifier/bin/verifier ./examples/src/test/java/com/aws/jverify/examples/Postconditions.java
```

or on Windows:

```
./verifier/build/install/verifier/bin/verifier.bat ./examples/src/test/java/com/aws/jverify/examples/Postconditions.java
```

You should see verification succeed with no errors.

If the `JVERIFY_STRATA` environment variable is not set, pass the Strata binary path explicitly: `--strata ./Strata/.lake/build/bin/strata`.

Try editing `Postconditions.java` to introduce a bug — for example, change `return x + 1;` to `return x + 2;` — and re-run. JVerify will report that the postcondition no longer holds.

## Adding JVerify to a Java project

To verify your own code, add the JVerify library as a compile-time dependency so you can use annotations such as `@Pure` and specification methods such as `precondition`. In a Gradle build script:

```kotlin
dependencies {
    implementation("com.aws.jverify:library:1.0-SNAPSHOT")
    // ...
}
```

Because the JVerify packages are not yet published to any shared repositories, run `./gradlew publishToMavenLocal` first so this can be resolved. See [the sample project](https://github.com/strata-org/jverify/blob/main/sample-project) for more details.

## Current limitations

The Strata back-end is still being extended to cover Java. Many programs that worked with JVerify's original back-end do not verify yet. See [Supported Java features](./supported_java_features.md) and issue [#384](https://github.com/strata-org/jverify/issues/384) for the current state.
