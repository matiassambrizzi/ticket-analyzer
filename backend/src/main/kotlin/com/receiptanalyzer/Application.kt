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
