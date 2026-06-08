plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.metro)
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    api(ktorLibs.server.core)
    api(ktorLibs.server.auth)
    api(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.statusPages)
    implementation(libs.exposed.core)
    implementation(libs.exposed.datetime)
    api(libs.opentelemetry.exporterOtlp)
    api(libs.opentelemetry.ktorInstrumentation)
    api(libs.opentelemetry.sdkAutoconfigure)
    api(libs.opentelemetry.semconv)
    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.contentNegotiation)
    testImplementation(ktorLibs.server.statusPages)
    testImplementation(ktorLibs.server.testHost)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    publications.withType<MavenPublication>().configureEach {
        pom {
            name = "Lighthouse Core"
            description = "Core interfaces and application helpers used by Lighthouse applications and generators."
        }
    }
}
