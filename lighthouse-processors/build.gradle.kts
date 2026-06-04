plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":lighthouse-annotations"))
    implementation(libs.ksp.symbol.processing.api)

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

val processorCoverageClasses = layout.buildDirectory.dir("classes/kotlin/main").map {
    fileTree(it) {
        exclude(
            "com/midstane/lighthouse/repository/processor/CrudRepositoryProcessor*",
            "com/midstane/lighthouse/repository/processor/crud/CrudRepositoryModelBuilder*",
        )
    }
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(processorCoverageClasses)
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(processorCoverageClasses)
    violationRules {
        rule {
            limit {
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
