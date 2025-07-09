# Verify a Java program

Let's try running JVerify on the following Java program,
found [here](https://github.com/aws/jverify/blob/main/examples/src/test/java/com/aws/jverify/examples/BinarySearch.java):

```java
{{#include ../../../examples/src/test/java/com/aws/jverify/examples/BinarySearch.java}}
```

To run JVerify, run the following from the repository root:

```
./verifier/build/install/verifier/bin/verifier ./examples/src/test/java/com/aws/jverify/examples/BinarySearch.java --dafny ./dafny/Scripts/dafny
```

or the following in Windows:

```
./verifier/build/install/verifier/bin/verifier.bat ./examples/src/test/java/com/aws/jverify/examples/BinarySearch.java --dafny ./dafny/Binaries/Dafny.exe
```

You should see the following output:

```
Dafny program verifier finished with 3 verified, 0 errors
```

This indicates that JVerify successfully verified 3 batches of specifications.

You can remove the need for the `--dafny` option by configuring the environment variable `JVERIFY_DAFNY` instead, or modifying your `PATH` so that `dafny` resolves automatically.

Try editing the `BinarySearch.java` file to see a verification failure. Almost any change you make to the meaning of the program will cause a verification file. For example, if you change `var lo = 0;` into `var lo = 1;`, JVerify returns:

```
BinarySearch.java(30:23-30:31): Error: this loop invariant could not be proved on entry
Related message: loop invariant violation
BinarySearch.java(32:23-32:60): Error: this loop invariant could not be proved on entry
Related message: loop invariant violation
Dafny program verifier finished with 2 verified, 2 errors
```

## Adding JVerify to a Java project

To start verifying your own code, you just need to add the JVerify library as a compile-time dependency
so that you can use annotations such as `@Pure` and specification methods such as `precondition` to your code.
In a Gradle build script, for example:

```kotlin
dependencies {
    // Depend on the library for specification annotations and methods
    implementation("com.aws.jverify:library:1.0-SNAPSHOT")
    ...
}
```

Because the JVerify packages are not yet published to any shared repositories,
you will need to run `./gradlew publishToMavenLocal` first so this can be resolved.
See [the sample project](https://github.com/aws/jverify/blob/main/sample-project) for more details.

## Gradual verification

If some of your code can't yet be verified, especially if it uses [features that aren't supported yet](supported_java_features.md),
you can control which code elements are verified with [the `@Verify` annotation](https://github.com/aws/jverify/blob/main/library/src/main/java/com/aws/jverify/Verify.java)
and the `--verify-by-default` CLI parameter.

Passing `--verify-by-default false` means that only elements explicitly annotated with `@Verify`,
or beneath elements with `@Verify(value = true, overrideChildren = true)`,
will be verified.

### GitHub issues related to the above:

- Let the number of reported verified things match Java concepts: https://github.com/aws/jverify/issues/127
- Support showing diagnostics using snippets: https://github.com/aws/jverify/issues/128
- Support running verification from the annotation processor as well: https://github.com/aws/jverify/issues/159
- Avoid needing to add `--add-export` when using the javac-plugin: https://github.com/aws/jverify/issues/160
