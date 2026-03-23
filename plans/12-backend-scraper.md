# Plan 12 — Backend: Scraper de Precios

## Objetivo
Implementar scrapers para supermercados argentinos online (Carrefour, Coto, Jumbo, Día) que alimenten la tabla `price_observations` con precios actuales.

## Pre-requisitos
- Plan 10 completado (PriceRepository)
- Plan 09 completado (ProductMatcher)

## Pasos

### 1. Agregar Jsoup en build.gradle.kts

```kotlin
implementation("org.jsoup:jsoup:1.17.2")
```

### 2. Interfaz y modelo base

`backend/src/main/kotlin/com/receiptanalyzer/scraper/StoreScraper.kt`:

```kotlin
package com.receiptanalyzer.scraper

data class ScrapedProduct(
    val name: String,
    val ean: String?,
    val price: Double,
    val category: String?,
    val imageUrl: String?,
    val isPromo: Boolean = false,
    val promoLabel: String? = null,
)

interface StoreScraper {
    val chainName: String
    suspend fun scrapeCategory(category: String): List<ScrapedProduct>
    suspend fun scrapeAll(): List<ScrapedProduct>
}
```

### 3. Carrefour Scraper (API REST)

Carrefour Argentina usa una API REST interna detrás de su buscador:

`backend/src/main/kotlin/com/receiptanalyzer/scraper/stores/CarrefourScraper.kt`:

```kotlin
package com.receiptanalyzer.scraper.stores

import com.receiptanalyzer.scraper.ScrapedProduct
import com.receiptanalyzer.scraper.StoreScraper
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.json.*

class CarrefourScraper(private val httpClient: HttpClient) : StoreScraper {

    override val chainName = "Carrefour"

    private val baseUrl = "https://www.carrefour.com.ar/api/catalog_system/pub/products/search"
    private val categories = listOf(
        "Almacen", "Bebidas", "Lacteos", "Carnes", "Verduras-y-frutas",
        "Limpieza", "Higiene-y-belleza", "Panaderia",
    )

    override suspend fun scrapeAll(): List<ScrapedProduct> =
        categories.flatMap { scrapeCategory(it) }

    override suspend fun scrapeCategory(category: String): List<ScrapedProduct> {
        val products = mutableListOf<ScrapedProduct>()
        var from = 0
        val pageSize = 50

        while (true) {
            // Rate limiting
            kotlinx.coroutines.delay(1000)

            val response: JsonArray = try {
                httpClient.get(baseUrl) {
                    parameter("fq", "C:/${category}/")
                    parameter("_from", from)
                    parameter("_to", from + pageSize - 1)
                    header("User-Agent", "Mozilla/5.0 (compatible; receipt-analyzer/1.0)")
                }.body()
            } catch (e: Exception) {
                break
            }

            if (response.isEmpty()) break

            response.forEach { item ->
                val obj = item.jsonObject
                val name = obj["productName"]?.jsonPrimitive?.content ?: return@forEach
                val items = obj["items"]?.jsonArray ?: return@forEach
                val firstItem = items.firstOrNull()?.jsonObject ?: return@forEach
                val ean = firstItem["ean"]?.jsonPrimitive?.content
                val sellers = firstItem["sellers"]?.jsonArray ?: return@forEach
                val price = sellers.firstOrNull()
                    ?.jsonObject?.get("commertialOffer")
                    ?.jsonObject?.get("Price")
                    ?.jsonPrimitive?.doubleOrNull ?: return@forEach
                val listPrice = sellers.firstOrNull()
                    ?.jsonObject?.get("commertialOffer")
                    ?.jsonObject?.get("ListPrice")
                    ?.jsonPrimitive?.doubleOrNull ?: price
                products.add(ScrapedProduct(
                    name = name,
                    ean = ean,
                    price = price,
                    category = category,
                    imageUrl = null,
                    isPromo = price < listPrice,
                    promoLabel = if (price < listPrice) "Precio especial" else null,
                ))
            }

            if (response.size < pageSize) break
            from += pageSize
        }

        return products
    }
}
```

### 4. Coto Scraper (HTML)

```kotlin
package com.receiptanalyzer.scraper.stores

import com.receiptanalyzer.scraper.ScrapedProduct
import com.receiptanalyzer.scraper.StoreScraper
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.jsoup.Jsoup

class CotoScraper(private val httpClient: HttpClient) : StoreScraper {

    override val chainName = "Coto"
    private val baseUrl = "https://www.cotodigital3.com.ar/sitios/cdigi/browse"
    private val categories = listOf(
        "almacen", "bebidas", "lacteos", "carnes", "frutas-y-verduras",
    )

    override suspend fun scrapeAll(): List<ScrapedProduct> =
        categories.flatMap { scrapeCategory(it) }

    override suspend fun scrapeCategory(category: String): List<ScrapedProduct> {
        val products = mutableListOf<ScrapedProduct>()
        var page = 0

        while (true) {
            kotlinx.coroutines.delay(1200)
            val html = try {
                httpClient.get("$baseUrl/$category") {
                    parameter("No", page * 24)
                    header("User-Agent", "Mozilla/5.0 (compatible; receipt-analyzer/1.0)")
                }.bodyAsText()
            } catch (e: Exception) { break }

            val doc = Jsoup.parse(html)
            val items = doc.select(".product_info_container")
            if (items.isEmpty()) break

            items.forEach { el ->
                val name = el.select(".descrip_full").text().takeIf { it.isNotBlank() } ?: return@forEach
                val priceText = el.select(".atg_store_newPrice").text()
                    .replace("$", "").replace(".", "").replace(",", ".").trim()
                val price = priceText.toDoubleOrNull() ?: return@forEach
                products.add(ScrapedProduct(name = name, ean = null, price = price, category = category, imageUrl = null))
            }

            if (items.size < 24) break
            page++
        }

        return products
    }
}
```

### 5. ScraperService.kt

```kotlin
package com.receiptanalyzer.scraper

import com.receiptanalyzer.price.PriceRepository
import com.receiptanalyzer.product.ProductMatcher
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

class ScraperService(
    private val scrapers: List<StoreScraper>,
    private val productMatcher: ProductMatcher,
    private val priceRepository: PriceRepository,
    private val storeResolver: (chainName: String) -> UUID?,
) {
    suspend fun runAll() {
        scrapers.forEach { scraper ->
            val storeId = storeResolver(scraper.chainName) ?: return@forEach
            runScraper(scraper, storeId)
        }
    }

    private suspend fun runScraper(scraper: StoreScraper, storeId: UUID) {
        val products = try {
            scraper.scrapeAll()
        } catch (e: Exception) {
            // Log error
            return
        }

        products.forEach { scraped ->
            val productId = productMatcher.matchOrCreate(
                rawName = scraped.name,
                ean = scraped.ean,
                category = scraped.category,
                storeChain = scraper.chainName,
            )
            priceRepository.insert(
                productId = productId,
                storeId = storeId,
                price = scraped.price,
                source = "scraper",
                isPromo = scraped.isPromo,
                promoLabel = scraped.promoLabel,
            )
        }
    }
}
```

### 6. ScraperScheduler.kt (cron interno)

```kotlin
package com.receiptanalyzer.scraper

import io.ktor.server.application.*
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.hours

fun Application.startScraperScheduler(scraperService: ScraperService) {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    scope.launch {
        while (isActive) {
            try {
                log.info("Starting daily scrape run")
                scraperService.runAll()
                log.info("Scrape run completed")
            } catch (e: Exception) {
                log.error("Scrape run failed", e)
            }
            delay(24.hours)
        }
    }
}
```

## Consideraciones

- **Rate limiting**: 1 req/seg mínimo entre peticiones por tienda.
- **Robots.txt**: verificar antes de scraping; los sitios listados permiten indexación.
- **Mantenimiento**: Los scrapers HTML son frágiles; cuando falla un scraper, el sistema sigue funcionando sin datos de esa tienda (no bloquea).
- **Datos propios primero**: El valor del sistema crece con los tickets de usuarios. El scraping enriquece pero no es bloqueante para el MVP.

## Verificación

1. `./gradlew run` → el scheduler arranca en segundo plano.
2. Verificar en los logs que la corrida de scraping empieza.
3. Consultar `scrape_runs` en Supabase → debe aparecer una fila con status `completed`.
4. Consultar `price_observations` → nuevas filas con `source = 'scraper'`.
5. Probar el endpoint de comparación con un producto que ahora tiene datos de scraping.

## Archivos que se crean
- `backend/src/main/kotlin/com/receiptanalyzer/scraper/StoreScraper.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/scraper/ScraperService.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/scraper/ScraperScheduler.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/scraper/stores/CarrefourScraper.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/scraper/stores/CotoScraper.kt`
- `backend/build.gradle.kts` (agregar Jsoup)
- `backend/src/main/kotlin/com/receiptanalyzer/Application.kt` (registrar scheduler)
