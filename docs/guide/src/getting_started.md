# Getting Started

To get started with JVerify, first follow the directions in the [Installation manual](https://github.com/verus-lang/verus/blob/main/INSTALL.md) to build it.

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

This indicates that JVerify successfully verified 3 parts.

Try editing the `BinarySearch.java` file to see a verification failure. Almost any change you make to the meaning of the program will cause a verification file. For example, if you change `var lo = 0;` into `var lo = 1;`, JVerify returns:

```
BinarySearch.java(30:23-30:31): Error: this loop invariant could not be proved on entry
Related message: loop invariant violation
BinarySearch.java(32:23-32:60): Error: this loop invariant could not be proved on entry
Related message: loop invariant violation
Dafny program verifier finished with 2 verified, 2 errors
```

### GitHub issues related to the above:
- Let the number of reported verified things match Java concepts: https://github.com/aws/jverify/issues/127
- Support showing diagnostics using snippets: https://github.com/aws/jverify/issues/128
