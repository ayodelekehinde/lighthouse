plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    group = "com.midstane"
    version = "1.0.0-SNAPSHOT"
}
