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
