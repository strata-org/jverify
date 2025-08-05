import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.gradle.ext.ActionDelegationConfig
import org.jetbrains.gradle.ext.delegateActions
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings

plugins {
    id("java")
    application
    id("java-library")

    id("org.jetbrains.gradle.plugin.idea-ext") version "1.1.10"
}

allprojects {
    group = "com.aws.jverify"
    version = "1.0-SNAPSHOT"

    apply(plugin = "java")

    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            url = uri("https://www.jetbrains.com/intellij-repository/releases")
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(23))
    }
}

idea {
    project {
        settings {
            delegateActions {
                // Run tests using IntelliJ instead of Gradle, since Gradle can't yet run individual dynamic tests.
                // More context:
                //  - <https://github.com/gradle/gradle/issues/19897>
                //  - <https://github.com/gradle/gradle/issues/21302>
                testRunner = ActionDelegationConfig.TestRunner.PLATFORM
            }

            runConfigurations {
                defaults(org.jetbrains.gradle.ext.JUnit::class.java) {
                    vmParameters = createJavacExports(listOf("ALL-UNNAMED"))
                        .joinToString(" ")
                }
            }
        }
    }
}

project(":library") {
    java {
        toolchain {
            // Use Java 17 for this subproject, so Java 17 projects can depend on it at compile-/run-time
            languageVersion = JavaLanguageVersion.of(17)
        }
    }
}

project(":examples") {
    dependencies {
        testImplementation(project(":library"))

        testImplementation(project(":test-engine"))
        testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        // https://mvnrepository.com/artifact/net.jqwik/jqwik-api
        testImplementation("net.jqwik:jqwik-api:1.9.2")
        
        implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
        implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    }

    tasks.test {
        useJUnitPlatform()
        jvmArgs = listOf(
        )
    }

    tasks.withType<Test> {
        jvmArgs = createJavacExports(listOf("ALL-UNNAMED"))
    }
}

project(":javaTypesGenerator") {

    apply(plugin = "application")
    application {
        mainClass.set("com.aws.jverify.generator.Main")
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

fun createJavacExports(targets: List<String>): List<String> {
    val javacPackages = listOf(
        "jdk.compiler/com.sun.tools.javac.api",
        "jdk.compiler/com.sun.tools.javac.file",
        "jdk.compiler/com.sun.tools.javac.util",
        "jdk.compiler/com.sun.tools.javac.tree",
        "jdk.compiler/com.sun.tools.javac.code",
        "jdk.compiler/com.sun.tools.javac.parser",
        "jdk.compiler/com.sun.tools.javac.jvm",
        "jdk.compiler/com.sun.tools.javac.comp",
        "jdk.compiler/com.sun.tools.javac.model",
        "jdk.compiler/com.sun.tools.javac.processing",
        "jdk.compiler/com.sun.tools.javac.main",
        "jdk.compiler/com.sun.tools.javac.resources"
    )

    return javacPackages.flatMap { pkg ->
        targets.map { target ->
            "--add-exports=$pkg=$target"
        }
    }
}

project(":common") {

    java {
        toolchain {
            // Use Java 17 for this subproject, so Java 17 projects can depend on it at compile-/run-time
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    dependencies {
        implementation(project(":library"))

        // Explicit junit-platform-launcher dependency ensures alignment of JUnit artifacts on test runtime classpath.
        // See also: <https://junit.org/junit5/docs/current/user-guide/#running-tests-build-gradle-bom>
        testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
        jvmArgs = listOf(
        )
    }

    tasks.withType<JavaCompile> {
    }
}

project(":javac-plugin") {

    java {
        toolchain {
            // Use Java 17 for this subproject, so Java 17 projects can depend on it at compile-/run-time
            languageVersion = JavaLanguageVersion.of(17)
        }
    }

    dependencies {
        implementation(project(":common"))
        implementation(project(":library"))

        implementation("com.google.auto.service:auto-service-annotations:1.0.1")
        annotationProcessor("com.google.auto.service:auto-service:1.0.1")

        testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
        jvmArgs = listOf(
        )
    }

    tasks.withType<JavaExec> {
        jvmArgs = createJavacExports(listOf("ALL-UNNAMED"))
    }
    tasks.withType<Test> {
        jvmArgs = createJavacExports(listOf("ALL-UNNAMED"))
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(createJavacExports(listOf("com.aws.jverify.plugin")))
        //options.compilerArgs.add("-proc:none")
    }

    tasks.register<JavaExec>("compileWithJVerify") {
        description = "Runs javac with the JVerify plugin"

        mainClass.set("com.sun.tools.javac.Main")

        classpath = files(
            tasks.jar.flatMap { it.archiveFile },
            configurations.getByName("runtimeClasspath")
        )

        val taskArgs = mutableListOf(
            "-processor", "com.aws.jverify.plugin.JVerifyProcessor",
            "-processorpath", tasks.jar.get().archiveFile.get().asFile.absolutePath
        )

        if (project.hasProperty("javacArgs")) {
            taskArgs.addAll((project.property("javacArgs") as String).split(Regex("[,=]")))
        }
        args = taskArgs

        dependsOn("jar")
    }
}

// A separate project for the plugin tests,
// because they need Java 23 and preview mode,
// but we want to support Java 17 user projects.
project(":javac-plugin-test") {

    dependencies {
        implementation(project(":javac-plugin"))
        implementation(project(":library"))
        implementation(project(":common"))

        // https://mvnrepository.com/artifact/com.google.testing.compile/compile-testing
        testImplementation("com.google.testing.compile:compile-testing:0.21.0")

        // https://mvnrepository.com/artifact/org.ow2.asm/asm
        testImplementation("org.ow2.asm:asm:9.7.1")

        testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.test {
        useJUnitPlatform()
        jvmArgs = listOf(
        )
    }

    tasks.withType<JavaExec> {
        jvmArgs = createJavacExports(listOf("ALL-UNNAMED")).
            // Using preview mode, so we can use the package java.lang.classfile
        plus("--enable-preview")
    }
    tasks.withType<Test> {
        jvmArgs = createJavacExports(listOf("ALL-UNNAMED")).

            // Using preview mode, so we can use the package java.lang.classfile
        plus("--enable-preview")
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(createJavacExports(listOf("com.aws.jverify.plugin")))
        //options.compilerArgs.add("-proc:none")
        // Using preview mode, so we can use the package java.lang.classfile
        options.compilerArgs.add("--enable-preview")
    }
}

project(":verifier") {

    apply(plugin = "application")
    application {
        mainClass.set("com.aws.jverify.verifier.Main")  // For a file named main.kt

        applicationDefaultJvmArgs = createJavacExports(listOf("ALL-UNNAMED"))
    }

    dependencies {
        implementation(project(":common"))
        implementation(project(":library"))

        implementation("org.jgrapht:jgrapht-core:1.5.2")

        // https://mvnrepository.com/artifact/org.checkerframework/checker-qual
        implementation("org.checkerframework:checker-qual:3.49.0")

        testImplementation(project(":test-engine"))
        testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")

        testImplementation("org.hamcrest:hamcrest:2.2")
        testImplementation("org.hamcrest:hamcrest-library:2.2")

        implementation("info.picocli:picocli:4.7.6")

        // Optional: annotation processor for compile-time checking
        annotationProcessor("info.picocli:picocli-codegen:4.7.6")

        implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    }

    tasks.test {
        useJUnitPlatform()
        jvmArgs = listOf(
        )
    }

    tasks.withType<JavaExec> {
        if (DefaultNativePlatform.getCurrentOperatingSystem().isWindows) {
            environment("JVERIFY_DAFNY", project.file("../dafny/Binaries/Dafny.exe").absolutePath)
        } else {
            environment("JVERIFY_DAFNY", project.file("../dafny/Scripts/dafny").absolutePath)
        }

        standardInput = System.`in`
        standardOutput = System.out

        jvmArgs = createJavacExports(listOf("ALL-UNNAMED", "com.aws.jverify.verifier"))
    }
    tasks.withType<Test> {
        jvmArgs = createJavacExports(listOf("ALL-UNNAMED"))
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.addAll(createJavacExports(listOf("com.aws.jverify.verifier")))
    }

    tasks.named("run", JavaExec::class) {
        // Override the jvmArgs to remove unwanted options
        jvmArgs = createJavacExports(listOf("ALL-UNNAMED"))

        // Keep the standard I/O settings
        standardInput = System.`in`
        standardOutput = System.out
    }
}

project(":test-engine") {
    apply(plugin = "java-library")

    dependencies {
        implementation(project(":common"))
        implementation(project(":verifier"))

        implementation("org.junit.jupiter:junit-jupiter")
        // Must be transitive in order to export our engine, which subclasses HierarchicalTestEngine
        api("org.junit.platform:junit-platform-engine:1.12.2")

        implementation("org.hamcrest:hamcrest:2.2")
        implementation("org.hamcrest:hamcrest-library:2.2")

        implementation("org.checkerframework:checker-qual:3.49.0")

        // for test engine registration
        implementation("com.google.auto.service:auto-service-annotations:1.0.1")
        annotationProcessor("com.google.auto.service:auto-service:1.0.1")
    }
}

subprojects {
    apply(plugin = "maven-publish")

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
        }
    }
}
