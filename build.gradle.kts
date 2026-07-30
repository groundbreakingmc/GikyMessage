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
    compileOnly("net.kyori:adventure-text-serializer-gson:4.26.1")
    testImplementation("net.kyori:adventure-text-serializer-gson:4.26.1")
    // Source: https://mvnrepository.com/artifact/net.kyori/adventure-text-minimessage
    testImplementation("net.kyori:adventure-text-minimessage:4.26.1")

    // Source: https://mvnrepository.com/artifact/it.unimi.dsi/fastutil
    compileOnly("it.unimi.dsi:fastutil:8.5.18")
    testImplementation("it.unimi.dsi:fastutil:8.5.18")

    // Source: https://mvnrepository.com/artifact/org.junit/junit-bom
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
