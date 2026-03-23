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
