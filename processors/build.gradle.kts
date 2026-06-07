plugins {
    alias(libs.plugins.kotlin.jvm)
    jacoco
    `maven-publish`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":annotations"))
    implementation(libs.ksp.symbol.processing.api)

    testImplementation(kotlin("test"))
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name = "Lighthouse Processors"
                description = "KSP processors that generate Lighthouse repositories and mappers."
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

val processorCoverageClasses = layout.buildDirectory.dir("classes/kotlin/main").map {
    fileTree(it) {
        exclude(
            "**/com/midstane/lighthouse/repository/processor/CrudRepositoryProcessor*.class",
            "**/com/midstane/lighthouse/repository/processor/MapperProcessor*.class",
            "**/com/midstane/lighthouse/repository/processor/crud/CrudRepositoryModelBuilder*.class",
            "**/com/midstane/lighthouse/repository/processor/mapper/MapperModelBuilder*.class",
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
