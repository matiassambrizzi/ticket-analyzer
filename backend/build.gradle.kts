plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("io.ktor.plugin") version "3.4.1"
}

group = "com.receiptanalyzer"
version = "0.0.1"

application {
    mainClass.set("com.receiptanalyzer.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-status-pages")

    // Ktor client (Claude API, scrapers)
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:${property("exposed_version")}")
    implementation("org.jetbrains.exposed:exposed-dao:${property("exposed_version")}")
    implementation("org.jetbrains.exposed:exposed-jdbc:${property("exposed_version")}")
    implementation("org.jetbrains.exposed:exposed-java-time:${property("exposed_version")}")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Config
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    // Logging
    implementation("ch.qos.logback:logback-classic:${property("logback_version")}")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.10")
}

tasks.test {
    useJUnitPlatform()
}
