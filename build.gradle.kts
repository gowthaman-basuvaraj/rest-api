import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "1.7.21"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "gow.tha.man"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin:5.2.0")
    implementation("org.xerial:sqlite-jdbc:3.40.0.0")
    implementation("org.slf4j:slf4j-simple:2.0.5")

    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")

    implementation("com.github.ajalt:clikt:2.8.0")


    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("rest-api")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "api.Api"))
        }
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}