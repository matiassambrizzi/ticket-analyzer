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
