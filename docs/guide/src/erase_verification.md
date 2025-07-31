# Removing code for verification during compilation

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