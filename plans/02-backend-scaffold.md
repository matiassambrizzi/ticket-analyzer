# Plan 02 — Backend: Scaffolding Kotlin/Ktor

## Objetivo
Crear el proyecto Kotlin/Ktor con Gradle, estructura de paquetes, y un endpoint de salud funcional conectado a la DB de Supabase.

## Pre-requisitos
- Plan 01 completado (proyecto Supabase y schema creados)
- JDK 21 instalado (`java -version`)
- Variables de entorno del Plan 01

## Estructura a crear

```
backend/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .env                          # copia de .env.example con valores reales
└── src/
    ├── main/
    │   ├── kotlin/com/receiptanalyzer/
    │   │   ├── Application.kt
    │   │   ├── config/
    │   │   │   ├── AppConfig.kt
    │   │   │   ├── DatabaseConfig.kt
    │   │   │   └── Routing.kt
    │   │   └── health/
    │   │       └── HealthRoutes.kt
    │   └── resources/
    │       └── application.conf
    └── test/
        └── kotlin/com/receiptanalyzer/
            └── health/
                └── HealthRouteTest.kt
```

## Pasos

### 1. settings.gradle.kts

```kotlin
rootProject.name = "receipt-analyzer-backend"
```

### 2. gradle.properties

```properties
kotlin.code.style=official
ktor_version=3.1.1
kotlin_version=2.1.20
logback_version=1.5.12
exposed_version=0.57.0
```

### 3. build.gradle.kts

```kotlin
plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    id("io.ktor.plugin") version "3.1.1"
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
```

### 4. Application.kt

```kotlin
package com.receiptanalyzer

import com.receiptanalyzer.config.AppConfig
import com.receiptanalyzer.config.configureDatabase
import com.receiptanalyzer.config.configureRouting
import io.ktor.server.application.*
import io.ktor.server.netty.*

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val config = AppConfig.load()
    configureDatabase(config)
    configureRouting()
}
```

### 5. config/AppConfig.kt

```kotlin
package com.receiptanalyzer.config

import io.github.cdimascio.dotenv.dotenv

data class AppConfig(
    val databaseUrl: String,
    val supabaseUrl: String,
    val supabaseJwtSecret: String,
    val claudeApiKey: String,
) {
    companion object {
        fun load(): AppConfig {
            val env = dotenv { ignoreIfMissing = true }
            return AppConfig(
                databaseUrl = env["DATABASE_URL"],
                supabaseUrl = env["SUPABASE_URL"],
                supabaseJwtSecret = env["SUPABASE_JWT_SECRET"],
                claudeApiKey = env["CLAUDE_API_KEY"],
            )
        }
    }
}
```

### 6. config/DatabaseConfig.kt

```kotlin
package com.receiptanalyzer.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabase(config: AppConfig) {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.databaseUrl
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
        minimumIdle = 2
        idleTimeout = 600_000
        connectionTimeout = 30_000
    }
    val dataSource = HikariDataSource(hikariConfig)
    Database.connect(dataSource)
    log.info("Database connected")
}
```

### 7. config/Routing.kt

```kotlin
package com.receiptanalyzer.config

import com.receiptanalyzer.health.healthRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    install(ContentNegotiation) {
        json()
    }
    install(CORS) {
        anyHost() // restringir en producción
    }
    routing {
        healthRoutes()
    }
}
```

### 8. health/HealthRoutes.kt

```kotlin
package com.receiptanalyzer.health

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(val status: String, val version: String = "0.0.1")

fun Routing.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK, HealthResponse("ok"))
    }
}
```

### 9. resources/application.conf

```hocon
ktor {
    deployment {
        port = 8080
        port = ${?PORT}
    }
    application {
        modules = [ com.receiptanalyzer.ApplicationKt.module ]
    }
}
```

## Verificación

1. `cd backend && ./gradlew build` → debe compilar sin errores.
2. `./gradlew run` → servidor en `http://localhost:8080`.
3. `curl http://localhost:8080/health` → debe retornar `{"status":"ok","version":"0.0.1"}`.
4. Verificar en los logs que la conexión a la DB de Supabase fue exitosa.

## Archivos que se crean
- `backend/build.gradle.kts`
- `backend/settings.gradle.kts`
- `backend/gradle.properties`
- `backend/src/main/kotlin/com/receiptanalyzer/Application.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/config/AppConfig.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/config/DatabaseConfig.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/config/Routing.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/health/HealthRoutes.kt`
- `backend/src/main/resources/application.conf`
