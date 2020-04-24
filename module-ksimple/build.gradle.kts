import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        maven(url = "https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath("org.rm3l:datanucleus-gradle-plugin:1.5.0")
    }
}
apply(plugin = "org.rm3l.datanucleus-gradle-plugin")

plugins {
    id("org.springframework.boot") version "2.2.6.RELEASE"
    id("io.spring.dependency-management") version "1.0.9.RELEASE"
    kotlin("jvm") version "1.3.71"
    kotlin("plugin.spring") version "1.3.71"
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

group = "org.apache.isis"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(platform("org.apache.isis.core:isis:2.0.0-M3"))
    implementation("org.apache.isis.core:isis-applib:2.0.0-M3")
    implementation("org.apache.isis.persistence:isis-persistence-jdo-datanucleus5")
    implementation("org.apache.isis.testing:isis-testing-fixtures-applib:2.0.0-M3")
    implementation("org.apache.isis.viewer:isis-viewer-wicket-ui:2.0.0-M3")
    implementation("org.apache.isis.viewer:isis-viewer-restfulobjects-viewer:2.0.0-M3")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.apache.isis.mavendeps:isis-mavendeps-unittests:2.0.0-M3")
    testImplementation("org.apache.isis.mavendeps:isis-mavendeps-integtests:2.0.0-M3")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
    testImplementation("org.mockito:mockito-core:3.1.0")
    testImplementation("org.hamcrest:hamcrest:2.1")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    runtimeOnly("com.h2database:h2")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }

}

configurations.all {
    exclude("org.springframework.boot", "spring-boot-starter-logging")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}
