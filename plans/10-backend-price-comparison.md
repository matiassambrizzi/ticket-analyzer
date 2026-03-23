# Plan 10 — Backend: Comparación de Precios

## Objetivo
Implementar los endpoints que comparan precios de productos entre tiendas, usando datos de `price_observations` (propios + scraping).

## Pre-requisitos
- Plan 09 completado (products y price_observations pobladas)
- Función SQL `get_price_comparison` aplicada (Plan 01)

## Pasos

### 1. PriceRepository.kt

```kotlin
package com.receiptanalyzer.price

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

object PriceObservationsTable : Table("price_observations") {
    val id = uuid("id").autoGenerate()
    val productId = uuid("product_id")
    val storeId = uuid("store_id")
    val price = decimal("price", 12, 2)
    val source = text("source")
    val sourceId = uuid("source_id").nullable()
    val observedAt = timestamp("observed_at")
    val isPromo = bool("is_promo").default(false)
    val promoLabel = text("promo_label").nullable()
    override val primaryKey = PrimaryKey(id)
}

data class PriceComparison(
    val storeId: UUID,
    val chainName: String,
    val latestPrice: Double,
    val observedAt: Instant,
    val isPromo: Boolean,
    val savings: Double,           // cuánto se ahorra vs el precio del ticket original
    val savingsPct: Double,        // % de ahorro
)

class PriceRepository {

    suspend fun insert(
        productId: UUID,
        storeId: UUID,
        price: Double,
        source: String,
        sourceId: UUID? = null,
        isPromo: Boolean = false,
        promoLabel: String? = null,
    ) = newSuspendedTransaction {
        PriceObservationsTable.insert {
            it[this.productId] = productId
            it[this.storeId] = storeId
            it[this.price] = price.toBigDecimal()
            it[this.source] = source
            it[this.sourceId] = sourceId
            it[observedAt] = Instant.now()
            it[this.isPromo] = isPromo
            it[this.promoLabel] = promoLabel
        }
    }

    suspend fun getComparison(productId: UUID, originalPrice: Double): List<PriceComparison> =
        newSuspendedTransaction {
            exec(
                """
                SELECT store_id, chain_name, latest_price, observed_at, is_promo
                FROM get_price_comparison(?)
                ORDER BY latest_price ASC
                """,
                args = listOf(productId)
            ) { rs ->
                buildList {
                    while (rs.next()) {
                        val price = rs.getDouble("latest_price")
                        add(PriceComparison(
                            storeId = UUID.fromString(rs.getString("store_id")),
                            chainName = rs.getString("chain_name"),
                            latestPrice = price,
                            observedAt = rs.getTimestamp("observed_at").toInstant(),
                            isPromo = rs.getBoolean("is_promo"),
                            savings = originalPrice - price,
                            savingsPct = if (originalPrice > 0) (originalPrice - price) / originalPrice * 100 else 0.0,
                        ))
                    }
                }
            } ?: emptyList()
        }
}
```

### 2. PriceService.kt

```kotlin
package com.receiptanalyzer.price

import com.receiptanalyzer.receipt.ReceiptRepository
import java.util.UUID

data class ReceiptComparisonResult(
    val receiptId: UUID,
    val storeName: String,
    val items: List<ItemComparison>,
    val totalPaid: Double,
    val totalCheapest: Double,
    val potentialSavings: Double,
)

data class ItemComparison(
    val rawName: String,
    val unitPrice: Double,
    val alternatives: List<PriceComparison>,
    val cheapestPrice: Double?,
    val savings: Double,
)

class PriceService(
    private val receiptRepository: ReceiptRepository,
    private val priceRepository: PriceRepository,
) {
    suspend fun compareReceipt(receiptId: UUID): ReceiptComparisonResult {
        val receipt = receiptRepository.findById(receiptId)
            ?: error("Receipt not found: $receiptId")

        val itemComparisons = receipt.items.map { item ->
            val alternatives = if (item.productId != null) {
                priceRepository.getComparison(item.productId, item.unitPrice)
                    .filter { it.storeId != receipt.storeId } // excluir la tienda original
            } else emptyList()

            ItemComparison(
                rawName = item.rawName,
                unitPrice = item.unitPrice,
                alternatives = alternatives,
                cheapestPrice = alternatives.minOfOrNull { it.latestPrice },
                savings = alternatives.minOfOrNull { item.unitPrice - it.latestPrice }
                    ?.coerceAtLeast(0.0) ?: 0.0,
            )
        }

        val totalPaid = receipt.total
        val totalCheapest = itemComparisons.sumOf { it.cheapestPrice ?: it.unitPrice }

        return ReceiptComparisonResult(
            receiptId = receiptId,
            storeName = "Carrefour", // TODO: resolver desde store
            items = itemComparisons,
            totalPaid = totalPaid,
            totalCheapest = totalCheapest,
            potentialSavings = totalPaid - totalCheapest,
        )
    }
}
```

### 3. PriceRoutes.kt

```kotlin
package com.receiptanalyzer.price

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.priceRoutes(priceService: PriceService) {
    route("/api") {
        // Comparar todos los items de un ticket
        post("/compare/receipt/{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val result = priceService.compareReceipt(id)
            call.respond(result)
        }

        // Comparar un producto específico
        get("/products/{id}/prices") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val originalPrice = call.request.queryParameters["originalPrice"]?.toDoubleOrNull() ?: 0.0
            // TODO: implementar PriceRepository.getComparison directamente
            call.respond(HttpStatusCode.OK)
        }
    }
}
```

## Verificación

1. Asegurarse de tener price_observations de al menos 2 tiendas distintas para algún producto (por ejemplo, Yerba Amanda en Carrefour y en Coto).
2. `POST /api/compare/receipt/{id}` → retorna JSON con `potentialSavings`, `totalPaid`, `totalCheapest`, y alternativas por item.
3. Los items sin datos de otras tiendas deben retornar `alternatives: []` y `savings: 0`.

## Archivos que se crean
- `backend/src/main/kotlin/com/receiptanalyzer/price/PriceObservationsTable.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/price/PriceRepository.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/price/PriceService.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/price/PriceRoutes.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/config/Routing.kt` (agregar priceRoutes)
