import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.70"
    kotlin("plugin.spring") version "1.3.70"
    id("org.springframework.boot") version "2.2.6.RELEASE"
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(platform("org.apache.isis.core:isis:2.0.0-M3"))
    implementation("org.apache.isis.core:isis-applib:2.0.0-M3")
    implementation("org.apache.isis.persistence:isis-persistence-jdo-datanucleus5:2.0.0-SNAPSHOT")
    implementation("org.apache.isis.testing:isis-testing-fixtures-applib:2.0.0-M3")
    implementation("org.apache.isis.viewer:isis-viewer-wicket-ui:2.0.0-M3")
    implementation("org.apache.isis.viewer:isis-viewer-restfulobjects-viewer:2.0.0-M3")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.apache.isis.mavendeps:isis-mavendeps-unittests:2.0.0-M3")
    testImplementation("org.apache.isis.mavendeps:isis-mavendeps-integtests:2.0.0-M3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testImplementation("org.mockito:mockito-core:3.1.0")
    testImplementation("org.hamcrest:hamcrest:2.1")
}

configurations.all {
    exclude("org.springframework.boot", "spring-boot-starter-logging")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
