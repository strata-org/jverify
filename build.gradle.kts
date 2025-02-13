plugins {
    id("java")
}

group = "org.example"
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

project(":javaTypesGenerator") {
    dependencies {
        
        // https://mvnrepository.com/artifact/org.checkerframework/checker-qual
        implementation("org.checkerframework:checker-qual:3.49.0")
        
        // https://mvnrepository.com/artifact/com.squareup/javapoet
        implementation("com.squareup:javapoet:1.13.0")
    }
}

project(":jverify") {
    dependencies {
        // https://mvnrepository.com/artifact/org.checkerframework/checker-qual
        implementation("org.checkerframework:checker-qual:3.49.0")

        implementation(files("${System.getProperty("java.home")}/../lib/tools.jar"))
    }

    tasks.withType<JavaExec> {
        jvmArgs = listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.code=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=org.example"
        )
    }
    tasks.withType<Test> {
        jvmArgs = listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.code=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=org.example"
        )
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.code=org.example",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=org.example"
        ))
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


//    testImplementation(platform("org.junit:junit-bom:5.10.0"))
//    testImplementation("org.junit.jupiter:junit-jupiter")
//}

//tasks.test {
//    useJUnitPlatform()
//}
