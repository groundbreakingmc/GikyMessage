plugins {
    id("java")
    id("maven-publish")
}

group = "com.github.groundbreakingmc"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Source: https://mvnrepository.com/artifact/net.kyori/adventure-text-serializer-gson
    compileOnly(libs.adventure.gson)
    testImplementation(libs.adventure.gson)

    // Source: https://mvnrepository.com/artifact/net.kyori/adventure-text-minimessage
    testImplementation(libs.adventure.minimessage)

    // Source: https://mvnrepository.com/artifact/it.unimi.dsi/fastutil
    compileOnly(libs.fastutil)
    testImplementation(libs.fastutil)

    // Source: https://mvnrepository.com/artifact/org.junit/junit-bom
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)

    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }

    repositories {
        mavenLocal()
    }
}
