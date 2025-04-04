## Getting started

### Verify Java code using JVerify
- Checkout this repository and its submodules
- Run `dotnet build dafny/Source/Dafny.sln`
- Run `gradle installDist`
- Open the repository root in VSCode 
- Open .java files in the examples folder
- Java extensions for VSCode should work well on these examples
- Run the VSCode task "verify" on examples to get verification diagnostics

### Compile Java code using JVerify
- Checkout this repository and its submodules
- Run `gradle compileWithJVerify -PjavacArgs="<pathToYourJavaFile>,-d=<yourDesiredOutputDirectory>`, note that the directory path will be relative to the `javac-plugin` folder.
- Run the resulting class files using `java`, including the JVerify library on the class oath. For example: `java -cp <yourDesiredOutputDirectory>:library/build/libs/library.jar <fullQualifiedNameOfYourMainClass>`

## Security

See [CONTRIBUTING](CONTRIBUTING.md#security-issue-notifications) for more information.

## License

This project is licensed under the Apache-2.0 License.

