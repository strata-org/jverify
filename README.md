# JVerify

JVerify is a program verifier for Java. You state the intended behavior of Java code as contracts: preconditions, postconditions and loop invariants, written as ordinary Java calls. JVerify then proves that the code meets them, or reports each check it could not prove.

JVerify is built on top of [Strata](https://github.com/strata-org/Strata), a platform for program verification.

## Status

**JVerify is not yet ready for production use.** Java language support, user experience, and reliability are all early. You can still experiment with it today to get a feel for what it can do. See the user guide for a current list of [supported features](docs/guide/src/supported_java_features.md).

## Quick start

1. Build the verifier and put Java and the Strata back-end on your `PATH`, as described in [Installation](docs/guide/src/installation.md), steps 1 to 3.
2. From the directory that contains this README, verify the first example:

   ```
   ./bytecode-frontend/build/install/bytecode-verifier/bin/bytecode-verifier ./examples/src/test/java/org/strata/jverify/examples/GaussianSum.java
   ```

3. For this example a pass prints `Failed methods: 0` and `Skipped methods: 0`:

   ```
   Found 0 errors
   Verified methods: 2
   Failed methods: 0
   Skipped methods: 0
   ```

[Running JVerify](docs/guide/src/first_verification.md) explains the example and its output. [sample-project](sample-project/README.md) is a small Gradle project that uses JVerify.

## User guide

The guide lives in `docs/guide/src` and reads as plain Markdown: start at the [overview](docs/guide/src/overview.md) and follow the [table of contents](docs/guide/src/SUMMARY.md). To read it as a book, install [mdBook](https://rust-lang.github.io/mdBook/) and run `mdbook serve` in `docs/guide`.

## History

JVerify works on the bytecode that the Java compiler produces from your file. An earlier version worked on the Java source instead; it is archived on branch `archive/source-frontend` at commit `630a5ba334af66ead3602f291f5dc65e4a0699c2`.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.
