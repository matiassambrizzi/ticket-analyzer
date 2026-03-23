# Plan 05 — Backend: API de Tickets (Upload + Consulta)

## Objetivo
Implementar los endpoints REST para subir tickets, procesar el pipeline asíncrono y consultar resultados. Al finalizar este plan el MVP es funcional: se puede subir el PDF de Carrefour y ver los datos extraídos.

## Pre-requisitos
- Plan 04 completado (LlmStructurer funcionando)
- Schema de Supabase aplicado (Plan 01)

## Pasos

### 1. Tabla de modelos de dominio

`backend/src/main/kotlin/com/receiptanalyzer/receipt/model/Receipt.kt`:

```kotlin
package com.receiptanalyzer.receipt.model

import java.time.Instant
import java.util.UUID

data class Receipt(
    val id: UUID,
    val userId: UUID,
    val storeId: UUID?,
    val receiptDate: Instant?,
    val receiptNumber: String?,
    val invoiceType: String?,
    val cae: String?,
    val subtotal: Double?,
    val totalDiscount: Double,
    val total: Double,
    val ivaTotal: Double?,
    val paymentMethod: String?,
    val rawText: String?,
    val filePath: String?,
    val processingStatus: ProcessingStatus,
    val processingError: String?,
    val createdAt: Instant,
    val items: List<ReceiptItem> = emptyList(),
)

enum class ProcessingStatus { PENDING, EXTRACTING, STRUCTURING, COMPLETED, FAILED }

data class ReceiptItem(
    val id: UUID,
    val receiptId: UUID,
    val productId: UUID?,
    val rawName: String,
    val ean: String?,
    val category: String?,
    val quantity: Double,
    val unitPrice: Double,
    val ivaPct: Double?,
    val lineTotal: Double,
    val discount: Double,
    val discountLabel: String?,
    val netTotal: Double,
    val sortOrder: Int?,
)
```

### 2. Exposed Tables

`backend/src/main/kotlin/com/receiptanalyzer/receipt/ReceiptTable.kt`:

```kotlin
package com.receiptanalyzer.receipt

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ReceiptsTable : Table("receipts") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id")
    val storeId = uuid("store_id").nullable()
    val receiptDate = timestamp("receipt_date").nullable()
    val receiptNumber = text("receipt_number").nullable()
    val invoiceType = text("invoice_type").nullable()
    val cae = text("cae").nullable()
    val subtotal = decimal("subtotal", 12, 2).nullable()
    val totalDiscount = decimal("total_discount", 12, 2).default(java.math.BigDecimal.ZERO)
    val total = decimal("total", 12, 2)
    val ivaTotal = decimal("iva_total", 12, 2).nullable()
    val paymentMethod = text("payment_method").nullable()
    val rawText = text("raw_text").nullable()
    val filePath = text("file_path").nullable()
    val processingStatus = text("processing_status").default("pending")
    val processingError = text("processing_error").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object ReceiptItemsTable : Table("receipt_items") {
    val id = uuid("id").autoGenerate()
    val receiptId = uuid("receipt_id").references(ReceiptsTable.id)
    val productId = uuid("product_id").nullable()
    val rawName = text("raw_name")
    val ean = text("ean").nullable()
    val category = text("category").nullable()
    val quantity = decimal("quantity", 10, 3)
    val unitPrice = decimal("unit_price", 12, 2)
    val ivaPct = decimal("iva_pct", 5, 2).nullable()
    val lineTotal = decimal("line_total", 12, 2)
    val discount = decimal("discount", 12, 2).default(java.math.BigDecimal.ZERO)
    val discountLabel = text("discount_label").nullable()
    val netTotal = decimal("net_total", 12, 2)
    val sortOrder = integer("sort_order").nullable()
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
```

### 3. ReceiptRepository.kt

```kotlin
package com.receiptanalyzer.receipt

import com.receiptanalyzer.receipt.model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

class ReceiptRepository {

    suspend fun create(userId: UUID, filePath: String, total: Double): UUID =
        newSuspendedTransaction {
            ReceiptsTable.insert {
                it[this.userId] = userId
                it[this.filePath] = filePath
                it[this.total] = total.toBigDecimal()
                it[this.createdAt] = Instant.now()
            }[ReceiptsTable.id]
        }

    suspend fun updateStatus(id: UUID, status: ProcessingStatus, error: String? = null) =
        newSuspendedTransaction {
            ReceiptsTable.update({ ReceiptsTable.id eq id }) {
                it[processingStatus] = status.name.lowercase()
                if (error != null) it[processingError] = error
            }
        }

    suspend fun updateFromStructured(id: UUID, structured: com.receiptanalyzer.receipt.model.StructuredReceipt, rawText: String, storeId: UUID?) =
        newSuspendedTransaction {
            ReceiptsTable.update({ ReceiptsTable.id eq id }) {
                it[this.storeId] = storeId
                it[this.rawText] = rawText
                it[this.receiptDate] = java.time.Instant.parse("${structured.receiptMeta.date}T00:00:00Z")
                it[this.receiptNumber] = structured.receiptMeta.receiptNumber
                it[this.invoiceType] = structured.receiptMeta.invoiceType
                it[this.cae] = structured.receiptMeta.cae
                it[this.subtotal] = structured.totals.subtotal?.toBigDecimal()
                it[this.totalDiscount] = structured.totals.totalDiscount.toBigDecimal()
                it[this.total] = structured.totals.total.toBigDecimal()
                it[this.ivaTotal] = structured.totals.ivaTotal?.toBigDecimal()
                it[this.paymentMethod] = structured.totals.paymentMethod
                it[this.processingStatus] = "completed"
            }
            structured.items.forEachIndexed { index, item ->
                ReceiptItemsTable.insert {
                    it[receiptId] = id
                    it[rawName] = item.rawName
                    it[ean] = item.ean
                    it[category] = item.category
                    it[quantity] = item.quantity.toBigDecimal()
                    it[unitPrice] = item.unitPrice.toBigDecimal()
                    it[ivaPct] = item.ivaPct?.toBigDecimal()
                    it[lineTotal] = item.lineTotal.toBigDecimal()
                    it[discount] = item.discount.toBigDecimal()
                    it[discountLabel] = item.discountLabel
                    it[netTotal] = (item.lineTotal - item.discount).toBigDecimal()
                    it[sortOrder] = index
                    it[createdAt] = Instant.now()
                }
            }
        }

    suspend fun findByUser(userId: UUID, limit: Int = 20, offset: Long = 0): List<Receipt> =
        newSuspendedTransaction {
            ReceiptsTable.select { ReceiptsTable.userId eq userId }
                .orderBy(ReceiptsTable.createdAt, SortOrder.DESC)
                .limit(limit, offset)
                .map { it.toReceipt() }
        }

    suspend fun findById(id: UUID): Receipt? =
        newSuspendedTransaction {
            val receipt = ReceiptsTable.select { ReceiptsTable.id eq id }
                .firstOrNull()?.toReceipt() ?: return@newSuspendedTransaction null
            val items = ReceiptItemsTable.select { ReceiptItemsTable.receiptId eq id }
                .orderBy(ReceiptItemsTable.sortOrder)
                .map { it.toReceiptItem() }
            receipt.copy(items = items)
        }

    private fun ResultRow.toReceipt() = Receipt(
        id = this[ReceiptsTable.id],
        userId = this[ReceiptsTable.userId],
        storeId = this[ReceiptsTable.storeId],
        receiptDate = this[ReceiptsTable.receiptDate],
        receiptNumber = this[ReceiptsTable.receiptNumber],
        invoiceType = this[ReceiptsTable.invoiceType],
        cae = this[ReceiptsTable.cae],
        subtotal = this[ReceiptsTable.subtotal]?.toDouble(),
        totalDiscount = this[ReceiptsTable.totalDiscount].toDouble(),
        total = this[ReceiptsTable.total].toDouble(),
        ivaTotal = this[ReceiptsTable.ivaTotal]?.toDouble(),
        paymentMethod = this[ReceiptsTable.paymentMethod],
        rawText = this[ReceiptsTable.rawText],
        filePath = this[ReceiptsTable.filePath],
        processingStatus = ProcessingStatus.valueOf(this[ReceiptsTable.processingStatus].uppercase()),
        processingError = this[ReceiptsTable.processingError],
        createdAt = this[ReceiptsTable.createdAt],
    )

    private fun ResultRow.toReceiptItem() = ReceiptItem(
        id = this[ReceiptItemsTable.id],
        receiptId = this[ReceiptItemsTable.receiptId],
        productId = this[ReceiptItemsTable.productId],
        rawName = this[ReceiptItemsTable.rawName],
        ean = this[ReceiptItemsTable.ean],
        category = this[ReceiptItemsTable.category],
        quantity = this[ReceiptItemsTable.quantity].toDouble(),
        unitPrice = this[ReceiptItemsTable.unitPrice].toDouble(),
        ivaPct = this[ReceiptItemsTable.ivaPct]?.toDouble(),
        lineTotal = this[ReceiptItemsTable.lineTotal].toDouble(),
        discount = this[ReceiptItemsTable.discount].toDouble(),
        discountLabel = this[ReceiptItemsTable.discountLabel],
        netTotal = this[ReceiptItemsTable.netTotal].toDouble(),
        sortOrder = this[ReceiptItemsTable.sortOrder],
    )
}
```

### 4. ReceiptService.kt (orquesta el pipeline)

```kotlin
package com.receiptanalyzer.receipt

import com.receiptanalyzer.receipt.extraction.ExtractorFactory
import com.receiptanalyzer.receipt.structuring.LlmStructurer
import com.receiptanalyzer.receipt.model.ProcessingStatus
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class ReceiptService(
    private val repository: ReceiptRepository,
    private val extractorFactory: ExtractorFactory,
    private val llmStructurer: LlmStructurer,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    fun processAsync(receiptId: UUID, fileBytes: ByteArray, mimeType: String) {
        scope.launch {
            try {
                // Extracción de texto
                repository.updateStatus(receiptId, ProcessingStatus.EXTRACTING)
                val extractionResult = extractorFactory.extract(fileBytes, mimeType)

                // Estructuración con LLM
                repository.updateStatus(receiptId, ProcessingStatus.STRUCTURING)
                val structured = llmStructurer.structure(extractionResult.text)

                // Guardar en DB
                // (store matching se implementa en Plan 09)
                repository.updateFromStructured(receiptId, structured, extractionResult.text, storeId = null)

            } catch (e: Exception) {
                repository.updateStatus(receiptId, ProcessingStatus.FAILED, e.message)
            }
        }
    }
}
```

### 5. ReceiptRoutes.kt

```kotlin
package com.receiptanalyzer.receipt

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UploadResponse(val receiptId: String, val status: String)

fun Route.receiptRoutes(service: ReceiptService, repository: ReceiptRepository) {
    route("/api/receipts") {

        // POST /api/receipts — upload ticket
        post {
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001") // TODO: Plan 06 auth
            val multipart = call.receiveMultipart()
            var fileBytes: ByteArray? = null
            var mimeType = "application/octet-stream"
            var fileName = "receipt"

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    fileBytes = part.streamProvider().readBytes()
                    mimeType = part.contentType?.toString() ?: mimeType
                    fileName = part.originalFileName ?: fileName
                }
                part.dispose()
            }

            val bytes = fileBytes ?: return@post call.respond(HttpStatusCode.BadRequest, "No file provided")

            // Crear registro inicial
            val receiptId = repository.create(userId, "receipts/$userId/${UUID.randomUUID()}", 0.0)

            // Procesar de forma asíncrona
            service.processAsync(receiptId, bytes, mimeType)

            call.respond(HttpStatusCode.Accepted, UploadResponse(receiptId.toString(), "pending"))
        }

        // GET /api/receipts — listar tickets del usuario
        get {
            val userId = UUID.fromString("00000000-0000-0000-0000-000000000001") // TODO: auth
            val receipts = repository.findByUser(userId)
            call.respond(receipts.map { ReceiptSummaryDto.from(it) })
        }

        // GET /api/receipts/:id — detalle
        get("/{id}") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val receipt = repository.findById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(ReceiptDetailDto.from(receipt))
        }

        // GET /api/receipts/:id/status — status de procesamiento
        get("/{id}/status") {
            val id = call.parameters["id"]?.let { UUID.fromString(it) }
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val receipt = repository.findById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(mapOf("status" to receipt.processingStatus.name.lowercase()))
        }
    }
}
```

> **Nota**: Los DTOs de respuesta (`ReceiptSummaryDto`, `ReceiptDetailDto`) se crean como data classes `@Serializable` que mapean los modelos de dominio.

### 6. Registrar rutas en Routing.kt

Agregar al `configureRouting()`:

```kotlin
routing {
    healthRoutes()
    receiptRoutes(receiptService, receiptRepository)
}
```

Y construir las dependencias en `Application.module()`.

## Verificación end-to-end del MVP

1. `./gradlew run`
2. Subir el PDF de ejemplo:
   ```bash
   curl -X POST http://localhost:8080/api/receipts \
     -F "file=@receipt_1774179045000.pdf;type=application/pdf"
   ```
   → Retorna `{"receiptId":"...", "status":"pending"}`

3. Consultar el status (puede tardar ~5-10 segundos):
   ```bash
   curl http://localhost:8080/api/receipts/{id}/status
   ```
   → Debe llegar a `{"status":"completed"}`

4. Ver el detalle:
   ```bash
   curl http://localhost:8080/api/receipts/{id}
   ```
   → Debe mostrar los 6 productos con nombres, precios, EANs y descuentos correctos.

## Archivos que se crean/modifican
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/model/Receipt.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/ReceiptTable.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/ReceiptRepository.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/ReceiptService.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/ReceiptRoutes.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/config/Routing.kt` (modificar)
- `backend/src/main/kotlin/com/receiptanalyzer/Application.kt` (modificar)
