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
