import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

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

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
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
        implementation(project(":library"))
        
        // https://mvnrepository.com/artifact/net.jqwik/jqwik-api
        implementation("net.jqwik:jqwik-api:1.9.2")

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
        "jdk.compiler/com.sun.tools.javac.processing"
    )

    return javacPackages.flatMap { pkg ->
        targets.map { target ->
            "--add-exports=$pkg=$target"
        }
    }
}

project(":common") {
    dependencies {
        implementation(project(":library"))
        
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
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
    
    dependencies {
        implementation(project(":common"))
        implementation(project(":library"))
        
        implementation("com.google.auto.service:auto-service-annotations:1.0.1")
        annotationProcessor("com.google.auto.service:auto-service:1.0.1")
        
        // https://mvnrepository.com/artifact/com.google.testing.compile/compile-testing
        testImplementation("com.google.testing.compile:compile-testing:0.21.0")

        // https://mvnrepository.com/artifact/org.ow2.asm/asm
        testImplementation("org.ow2.asm:asm:9.7.1")

        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
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

project(":verifier") {

    apply(plugin = "application")
    application {
        mainClass.set("com.aws.jverify.verifier.Main")  // For a file named main.kt

        applicationDefaultJvmArgs = createJavacExports(listOf("ALL-UNNAMED"))
    }

    dependencies {
        implementation(project(":common"))
        implementation(project(":library"))
        
        // https://mvnrepository.com/artifact/org.checkerframework/checker-qual
        implementation("org.checkerframework:checker-qual:3.49.0")
        
        testImplementation(platform("org.junit:junit-bom:5.10.0"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.hamcrest:hamcrest:2.2")
        testImplementation("org.hamcrest:hamcrest-library:2.2")
        
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