plugins {
    kotlin("jvm") version "1.3.70"
    `maven-publish`
}

group = "cool.db"
version = "0.2"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.`java-time`)

    testImplementation(libs.postgre)
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.1.3")
    testImplementation("io.mockk:mockk:1.9.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.1")
    testImplementation("org.testcontainers:testcontainers:1.12.0")
    testImplementation("org.testcontainers:junit-jupiter:1.12.0")
    testImplementation("org.testcontainers:postgresql:1.12.0")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

tasks.test {
    useJUnitPlatform()
}


