repositories {
    jcenter()
}

dependencies {
    gradleApi()
}

plugins {
    `kotlin-dsl` apply true
    id("tanvd.kosogor") version "1.0.7" apply true
}