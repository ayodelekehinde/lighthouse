plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.metro)
}


kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(ktorLibs.server.core)
            implementation(libs.exposed.core)
            implementation(libs.exposed.datetime)
            api(libs.opentelemetry.exporterOtlp)
            api(libs.opentelemetry.ktorInstrumentation)
            api(libs.opentelemetry.sdkAutoconfigure)
            api(libs.opentelemetry.semconv)
        }

        commonTest.dependencies {
            kotlin("test")
        }
    }
}
