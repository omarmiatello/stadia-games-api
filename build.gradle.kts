plugins {
    kotlin("jvm") version "1.7.0-RC"
}

val kotlinVersion: String by extra("1.7.0-RC")
val kotlinCoroutinesVersion: String by extra("1.6.2")

allprojects {
    repositories {
        mavenCentral()
    }
}