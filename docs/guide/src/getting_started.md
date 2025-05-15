# Installation
JVerify can currently only be used by building it from source.

1. Ensure the .NET runtime version 8 or higher is installed on your machine. More information can be found [here](https://dotnet.microsoft.com/en-us/).
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
./verifier/build/install/verifier/bin/verifier.bat ./examples/src/test/java/com/aws/jverify/examples/BinarySearch.java --dafny ./dafny/Scripts/dafny
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

## Removing verification code from a Java program
JVerify allowed removing any code related to verification from your Java programs, to ensure that there is no runtime impact. 

- Set the current working directly to this repository
- Run `./gradlew compileWithJVerify -PjavacArgs="<pathToYourJavaFile>,-d=<yourDesiredOutputDirectory>`, note that the directory path will be relative to the `javac-plugin` folder.
- Run the resulting class files using `java`, including the JVerify library on the class path. For example: `java -cp <yourDesiredOutputDirectory>:library/build/libs/library.jar <fullQualifiedNameOfYourMainClass>`

### GitHub issues related to the above:
- Let the number of reported verified things match Java concepts: https://github.com/aws/jverify/issues/127
- Support showing diagnostics using snippets: https://github.com/aws/jverify/issues/128
