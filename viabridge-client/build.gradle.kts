plugins {
    `java-library`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    api(project(":protocol"))
    compileOnly("net.minestom:minestom:2026.07.12-26.2")
}
