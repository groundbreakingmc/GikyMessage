plugins {
    id("java")
    id("maven-publish")
}

group = "com.github.groundbreakingmc"
version = "1.0.0-beta"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.adventure.api)
    compileOnly(libs.annotations)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
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
