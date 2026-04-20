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

You should see the following output:

```
Found 0 errors
Verified methods: 3
Failed methods: 0
Skipped methods: 0
```

If the `JVERIFY_STRATA` environment variable is not set, pass the Strata project directory explicitly with `--strata ./Strata`.

Try editing `Postconditions.java` to introduce a bug — for example, change `return x + 1;` to `return x + 2;` in `addOne` — and re-run. JVerify reports:

```
Postconditions.java(8:36-8:48): Error: assertion does not hold
Postconditions.java(1:1-1:1): Error: assertion does not hold
Found 2 errors
Verified methods: 2
Failed methods: 1
Skipped methods: 0
```

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

JVerify currently supports a limited subset of Java. Many programs that worked with JVerify's original back-end do not verify yet. See [Supported Java features](./supported_java_features.md) and issue [#384](https://github.com/strata-org/jverify/issues/384) for the current state.

### Related open issues

- Let the number of reported verified things match Java concepts: [#127](https://github.com/strata-org/jverify/issues/127)
- Support showing diagnostics using snippets: [#128](https://github.com/strata-org/jverify/issues/128)
- Support running verification from the annotation processor as well: [#159](https://github.com/strata-org/jverify/issues/159)
- Avoid needing to add `--add-export` when using the javac-plugin: [#160](https://github.com/strata-org/jverify/issues/160)
