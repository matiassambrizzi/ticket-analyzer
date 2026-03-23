# Plan 04 — Backend: Estructuración con Claude API

## Objetivo
Integrar la Claude API para convertir el texto crudo de un ticket en datos estructurados (JSON). Este es el componente central del pipeline de procesamiento.

## Pre-requisitos
- Plan 03 completado (extracción de texto funcionando)
- `CLAUDE_API_KEY` en el archivo `.env`

## Modelo de datos estructurado

`backend/src/main/kotlin/com/receiptanalyzer/receipt/model/StructuredReceipt.kt`:

```kotlin
package com.receiptanalyzer.receipt.model

import kotlinx.serialization.Serializable

@Serializable
data class StructuredReceipt(
    val store: StoreInfo,
    val receiptMeta: ReceiptMeta,
    val items: List<StructuredItem>,
    val totals: ReceiptTotals,
)

@Serializable
data class StoreInfo(
    val chainName: String,
    val address: String? = null,
    val cuit: String? = null,
)

@Serializable
data class ReceiptMeta(
    val date: String,          // YYYY-MM-DD
    val time: String? = null,  // HH:MM:SS
    val receiptNumber: String? = null,
    val invoiceType: String? = null,
    val cae: String? = null,
)

@Serializable
data class StructuredItem(
    val rawName: String,
    val category: String? = null,
    val quantity: Double,
    val unitPrice: Double,
    val ivaPct: Double? = null,
    val lineTotal: Double,
    val ean: String? = null,
    val discount: Double = 0.0,
    val discountLabel: String? = null,
)

@Serializable
data class ReceiptTotals(
    val subtotal: Double? = null,
    val totalDiscount: Double = 0.0,
    val total: Double,
    val ivaTotal: Double? = null,
    val paymentMethod: String? = null,
)
```

## Pasos

### 1. Agregar dependencia Ktor client content negotiation (ya incluida en Plan 02)

No se requieren dependencias adicionales: se usa el Ktor HttpClient para llamar a la API de Anthropic directamente.

### 2. PromptTemplates.kt

`backend/src/main/kotlin/com/receiptanalyzer/receipt/structuring/PromptTemplates.kt`:

```kotlin
package com.receiptanalyzer.receipt.structuring

object PromptTemplates {

    fun receiptExtractionPrompt(rawText: String): String = """
        Sos un parser de tickets de supermercados argentinos. Dado el texto crudo de un ticket de compra,
        extraé los datos estructurados y retorná ÚNICAMENTE un objeto JSON válido (sin texto adicional, sin markdown).

        El JSON debe seguir exactamente este schema:
        {
          "store": { "chainName": string, "address": string|null, "cuit": string|null },
          "receiptMeta": {
            "date": "YYYY-MM-DD",
            "time": "HH:MM:SS"|null,
            "receiptNumber": string|null,
            "invoiceType": string|null,
            "cae": string|null
          },
          "items": [
            {
              "rawName": string,
              "category": string|null,
              "quantity": number,
              "unitPrice": number,
              "ivaPct": number|null,
              "lineTotal": number,
              "ean": string|null,
              "discount": number,
              "discountLabel": string|null
            }
          ],
          "totals": {
            "subtotal": number|null,
            "totalDiscount": number,
            "total": number,
            "ivaTotal": number|null,
            "paymentMethod": string|null
          }
        }

        Reglas:
        - Para productos pesados (ej: "1 x 0.310 x 32510.00"), "quantity" es el peso (0.310).
        - Asociá cada línea de descuento (ej: "MC BARRA CHOCO SIN -280.00") al item correspondiente.
        - Los EAN barcodes son los números de 13 dígitos que aparecen bajo cada producto.
        - Todos los montos en pesos argentinos como números (sin el símbolo $).
        - Las fechas en formato ISO (YYYY-MM-DD). El formato "22/03/26" es 2026-03-22.
        - Si una cadena de categoría está en el ticket (ej: "Almacen", "Fiambreria"), asignala al item.

        Texto del ticket:
        ---
        $rawText
        ---
    """.trimIndent()
}
```

### 3. ClaudeClient.kt

`backend/src/main/kotlin/com/receiptanalyzer/receipt/structuring/ClaudeClient.kt`:

```kotlin
package com.receiptanalyzer.receipt.structuring

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ClaudeClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ClaudeRequest(
        val model: String,
        val max_tokens: Int,
        val messages: List<Message>,
    )

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class ClaudeResponse(val content: List<ContentBlock>)

    @Serializable
    private data class ContentBlock(val type: String, val text: String)

    suspend fun complete(prompt: String): String {
        val response = httpClient.post("https://api.anthropic.com/v1/messages") {
            header("x-api-key", apiKey)
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(
                ClaudeRequest(
                    model = "claude-haiku-4-5-20251001",
                    max_tokens = 4096,
                    messages = listOf(Message("user", prompt)),
                )
            )
        }
        val body: ClaudeResponse = response.body()
        return body.content.first().text
    }
}
```

### 4. LlmStructurer.kt

`backend/src/main/kotlin/com/receiptanalyzer/receipt/structuring/LlmStructurer.kt`:

```kotlin
package com.receiptanalyzer.receipt.structuring

import com.receiptanalyzer.receipt.model.StructuredReceipt
import kotlinx.serialization.json.Json

class LlmStructurer(private val client: ClaudeClient) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun structure(rawText: String): StructuredReceipt {
        val prompt = PromptTemplates.receiptExtractionPrompt(rawText)
        val responseText = client.complete(prompt)
        // Limpiar posibles backticks o prefijos de markdown
        val cleanJson = responseText
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return json.decodeFromString<StructuredReceipt>(cleanJson)
    }
}
```

### 5. Test de integración (requiere API key real)

`backend/src/test/kotlin/com/receiptanalyzer/receipt/structuring/LlmStructurerIntegrationTest.kt`:

```kotlin
package com.receiptanalyzer.receipt.structuring

import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LlmStructurerIntegrationTest {

    @Test
    fun `structures Carrefour receipt correctly`() = runBlocking {
        val env = dotenv { ignoreIfMissing = true }
        val apiKey = env["CLAUDE_API_KEY"]

        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
        val client = ClaudeClient(httpClient, apiKey)
        val structurer = LlmStructurer(client)

        // Texto extraído del PDF de Carrefour (hardcodeado para el test)
        val rawText = """
            Carrefour
            326 ALVAREZ JONTE
            AV. ALVAREZ JONTE 4872
            INC SA - CUIT Nro:30-68731043-4
            FACTURA B (Cod.006)
            Fecha 22/03/26 Hora 11:30:43
            Almacen
            YERBA MATE AMANDA TRADICIONAL X 1 KG
            1 x 4650,00 (21.00%) 4650.00
            7792710000175
            BARRA CHOCO SIN TACC INTEGRA 38 X GRS
            2 x 1400,00 (21.00%) 2800.00
            MC BARRA CHOCO SIN -280.00
            7798343751057
            Fiambreria
            QUESO PATEGRAS MILKAUT TROZADO
            1 x 0.310 x 32510.00 (21.00%) 10078.10
            2507513003101
            TOTAL $ 26886.10
            Pago MERCADOPAGO $ 26886,10
        """.trimIndent()

        val result = structurer.structure(rawText)

        assertEquals("Carrefour", result.store.chainName)
        assertEquals("2026-03-22", result.receiptMeta.date)
        assertEquals(6, result.items.size)

        val yerba = result.items.first { it.ean == "7792710000175" }
        assertEquals("YERBA MATE AMANDA TRADICIONAL X 1 KG", yerba.rawName)
        assertEquals(1.0, yerba.quantity)
        assertEquals(4650.0, yerba.unitPrice)

        val queso = result.items.first { it.ean == "2507513003101" }
        assertEquals(0.310, queso.quantity, absoluteTolerance = 0.001)

        val barra = result.items.first { it.ean == "7798343751057" }
        assertEquals(280.0, barra.discount)

        assertEquals(26886.10, result.totals.total, absoluteTolerance = 0.01)
        assertEquals("MERCADOPAGO", result.totals.paymentMethod?.uppercase())
    }
}
```

> **Nota**: Este test hace una llamada real a la API. Marcar con `@Tag("integration")` para excluirlo de CI si se desea.

## Verificación

1. `./gradlew test --tests "*.LlmStructurerIntegrationTest"` → el test debe pasar con todos los assertions.
2. Inspeccionar el JSON retornado por Claude para confirmar que los 6 productos del ticket son extraídos correctamente con sus EAN, precios y descuentos.

## Archivos que se crean
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/model/StructuredReceipt.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/structuring/PromptTemplates.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/structuring/ClaudeClient.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/structuring/LlmStructurer.kt`
- `backend/src/test/kotlin/com/receiptanalyzer/receipt/structuring/LlmStructurerIntegrationTest.kt`
