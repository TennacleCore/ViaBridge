plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

dependencies {
    compileOnly("io.netty:netty-transport:4.1.115.Final") // UserConnection.getChannel()
    implementation(project(":protocol"))
    compileOnly("com.velocitypowered:velocity-api:4.0.0-SNAPSHOT")
    compileOnly("com.viaversion:viaversion-api:5.11.0")
    compileOnly("com.viaversion:viaversion-common:5.11.0")
    compileOnly("io.netty:netty-buffer:4.2.16.Final")
    annotationProcessor("com.velocitypowered:velocity-api:4.0.0-SNAPSHOT")
}

tasks.jar {
    dependsOn(":protocol:classes")
    from(project(":protocol").sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Implementation-Title"] = "ViaBridge"
        attributes["Implementation-Version"] = project.version
    }
}
