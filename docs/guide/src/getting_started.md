# Installation
JVerify can currently only be used by building it from source.

1. Ensure the .NET runtime version 8 or higher is installed on your machine. More information can be found [here](https://dotnet.microsoft.com/en-us/).
1. Ensure Java23 is installed on your machine. If you have a higher Java version installed add `-Dorg.gradle.java.home=path-to-local-java-to-be-use` to the gradle build command below.
1. Check out the [JVerify repository](https://github.com/aws/jverify) and its submodules, for example using `git clone https://github.com/aws/jverify --recurse-submodules`.
1. Navigate to the JVerify repository directory.
1. Run `./gradlew installDist` (for Mac OS or Linux systems) or `./gradlew.bat installDist` (for Windows)
1. Run `make -C dafny exe` to build [Dafny](https://dafny.org/), a verification tool used by JVerify.
1. Install Z3 using `make -C dafny z3-<OS>`, where `<OS>` can be `mac`, `mac-arm` or `ubuntu`. Pick the one that matches your OS. For systems that do not fit those categories, such as Windows, you will have to take these steps instead:
    1. Download the release of [Z3 version 4.12.1](https://github.com/Z3Prover/z3/releases/tag/z3-4.12.1) that matches your system.
    1. Unzip the content of the Z3 folder into `dafny\Binaries\z3` so that `dafny\Binaries\z3\bin\z3.exe` exists.

# Usage

## Verify a Java program

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

## Removing verification code from a Java program

JVerify supports removing any code related to verification from your Java programs, to ensure that there is no runtime impact. 

- Set the current working directly to this repository
- Run `./gradlew compileWithJVerify -PjavacArgs="<pathToYourJavaFile>,-d=<yourDesiredOutputDirectory>`, note that the directory path will be relative to the `javac-plugin` folder.
- Run the resulting class files using `java`, including the JVerify library on the class path. For example: `java -cp <yourDesiredOutputDirectory>:library/build/libs/library.jar <fullQualifiedNameOfYourMainClass>`

You can also add the JVerify annotation processor directly to your build configuration so this happens automatically:

```kotlin
dependencies {
    // Depend on the library for specification annotations and methods
    implementation("com.aws.jverify:library:1.0-SNAPSHOT")
    // Depend on the annotation processor to erase specification code when building
    annotationProcessor("com.aws.jverify:javac-plugin:1.0-SNAPSHOT")
    ...
}
```

This currently requires some additional JDK parameters to work, however: see [the sample project build script](https://github.com/aws/jverify/blob/main/sample-project/app/build.gradle.kts) for more details.

### GitHub issues related to the above:

- Let the number of reported verified things match Java concepts: https://github.com/aws/jverify/issues/127
- Support showing diagnostics using snippets: https://github.com/aws/jverify/issues/128
- Support running verification from the annotation processor as well: https://github.com/aws/jverify/issues/159
- Avoid needing to add `--add-export` when using the javac-plugin: https://github.com/aws/jverify/issues/160
