plugins {
    application
}

repositories {
    mavenCentral()
}

// Where `installDist` of the enclosing JVerify build puts the verifier and its jars.
val jverifyInstall = layout.projectDirectory.dir("../../bytecode-frontend/build/install/bytecode-verifier")

dependencies {
    // Specification methods and annotations: precondition, postcondition, @Verify, ...
    implementation(fileTree(jverifyInstall.dir("lib")) {
        include("library-*.jar")
        exclude("library-for-testing-*.jar")
    })

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "org.example.Main"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.register<Exec>("verify") {
    group = "verification"
    description = "Verifies App.java with JVerify."
    commandLine(
        jverifyInstall.file("bin/bytecode-verifier").asFile.path,
        "src/main/java/org/example/App.java",
    )
}
