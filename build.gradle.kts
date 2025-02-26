import org.gradle.internal.instrumentation.api.annotations.ParameterKind.VarargParameter

plugins {
    id("java")
    application
}

group = "com.aws.jverify"
version = "1.0-SNAPSHOT"

allprojects {
    apply(plugin = "java")
    
    repositories {
        mavenCentral()
        maven {
            url = uri("https://www.jetbrains.com/intellij-repository/releases")
        }
    }

}

project(":library") {
    
}

project(":javaTypesGenerator") {

    apply(plugin = "application")
    application {
        mainClass.set("com.aws.jverify.generator.Main")  // For a file named main.kt
    }
    
    dependencies {
        
        // https://mvnrepository.com/artifact/org.checkerframework/checker-qual
        implementation("org.checkerframework:checker-qual:3.49.0")
        
        // https://mvnrepository.com/artifact/com.squareup/javapoet
        implementation("com.squareup:javapoet:1.13.0")

        implementation("info.picocli:picocli:4.7.6")

        // Optional: annotation processor for compile-time checking
        annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    }
}

val allUnnamedArgs = listOf(
"--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
"--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
"--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
"--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
"--add-exports=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
"--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
"--add-exports=jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED")

val jverifyArgs = listOf(
    "--add-exports=jdk.compiler/com.sun.tools.javac.api=com.aws.jverify",
    "--add-exports=jdk.compiler/com.sun.tools.javac.file=com.aws.jverify",
    "--add-exports=jdk.compiler/com.sun.tools.javac.util=com.aws.jverify",
    "--add-exports=jdk.compiler/com.sun.tools.javac.tree=com.aws.jverify",
    "--add-exports=jdk.compiler/com.sun.tools.javac.code=com.aws.jverify",
    "--add-exports=jdk.compiler/com.sun.tools.javac.parser=com.aws.jverify",
    "--add-exports=jdk.compiler/com.sun.tools.javac.jvm=com.aws.jverify")

project(":verifier") {

    apply(plugin = "application")
    application {
        mainClass.set("com.aws.jverify.Main")  // For a file named main.kt

        applicationDefaultJvmArgs = allUnnamedArgs
    }
    
    dependencies {
        implementation(project(":library"))
        
        // https://mvnrepository.com/artifact/org.checkerframework/checker-qual
        implementation("org.checkerframework:checker-qual:3.49.0")

        implementation(files("${System.getProperty("java.home")}/../lib/tools.jar"))
        
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        
        implementation("info.picocli:picocli:4.7.6")

        // Optional: annotation processor for compile-time checking
        annotationProcessor("info.picocli:picocli-codegen:4.7.6")
    }

    tasks.test {
        useJUnitPlatform()
        jvmArgs = listOf(
        )
    }
    
    tasks.withType<JavaExec> {                
        standardInput = System.`in`
        standardOutput = System.out
        
        jvmArgs = allUnnamedArgs + jverifyArgs
    }
    tasks.withType<Test> {
        jvmArgs = allUnnamedArgs
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(jverifyArgs)
    }

    tasks.named("run", JavaExec::class) {
        // Override the jvmArgs to remove unwanted options
        jvmArgs = allUnnamedArgs

        // Keep the standard I/O settings
        standardInput = System.`in`
        standardOutput = System.out
    }
}

//dependencies {
    
    
    // https://mvnrepository.com/artifact/com.jetbrains.intellij.platform/util
//    implementation("com.jetbrains.intellij.platform:util:243.23654.166")
//
//// https://mvnrepository.com/artifact/com.jetbrains.intellij.platform/core
//    implementation("com.jetbrains.intellij.platform:core:243.23654.166")
//    
//// https://mvnrepository.com/artifact/com.jetbrains.intellij.platform/lang-impl
//    implementation("com.jetbrains.intellij.platform:lang-impl:243.23654.166")
//
//// https://mvnrepository.com/artifact/com.jetbrains.intellij.java/java-psi
//    implementation("com.jetbrains.intellij.java:java-psi:243.23654.153")


//}