plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://www.jetbrains.com/intellij-repository/releases")
    }
}
dependencies {
    implementation(files("${System.getProperty("java.home")}/../lib/tools.jar"))
    
    // https://mvnrepository.com/artifact/org.jsonschema2pojo/jsonschema2pojo-core
    implementation("org.jsonschema2pojo:jsonschema2pojo-core:1.2.2")
    // https://mvnrepository.com/artifact/com.squareup/javapoet
    implementation("com.squareup:javapoet:1.13.0")
    
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


    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
