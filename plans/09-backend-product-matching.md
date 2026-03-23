# Plan 09 — Backend: Matching de Productos

## Objetivo
Implementar la deduplicación de productos usando EAN como clave primaria y fuzzy matching con pg_trgm como fallback. Al subir un ticket, cada item queda vinculado a un producto canónico en la tabla `products`.

## Pre-requisitos
- Plan 05 completado (pipeline de tickets)
- Extensión `pg_trgm` habilitada en Supabase (Plan 01)

## Pasos

### 1. ProductTable.kt (Exposed)

```kotlin
package com.receiptanalyzer.product

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ProductsTable : Table("products") {
    val id = uuid("id").autoGenerate()
    val ean = text("ean").nullable()
    val canonicalName = text("canonical_name")
    val brand = text("brand").nullable()
    val category = text("category").nullable()
    val unit = text("unit").nullable()
    val unitQuantity = decimal("unit_quantity", 10, 3).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object ProductAliasesTable : Table("product_aliases") {
    val id = uuid("id").autoGenerate()
    val productId = uuid("product_id").references(ProductsTable.id)
    val storeChain = text("store_chain")
    val aliasName = text("alias_name")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
```

### 2. ProductRepository.kt

```kotlin
package com.receiptanalyzer.product

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

data class Product(
    val id: UUID,
    val ean: String?,
    val canonicalName: String,
    val brand: String?,
    val category: String?,
)

class ProductRepository {

    suspend fun findByEan(ean: String): Product? =
        newSuspendedTransaction {
            ProductsTable.select { ProductsTable.ean eq ean }
                .firstOrNull()?.toProduct()
        }

    // Fuzzy match usando pg_trgm similarity via raw SQL
    suspend fun findByFuzzyName(name: String, threshold: Double = 0.5): Product? =
        newSuspendedTransaction {
            val normalizedName = name.uppercase().trim()
            // Buscar en canonical_name y en aliases
            exec(
                """
                SELECT p.id, p.ean, p.canonical_name, p.brand, p.category,
                       similarity(p.canonical_name, ?) as sim
                FROM products p
                WHERE similarity(p.canonical_name, ?) > ?
                UNION
                SELECT p.id, p.ean, p.canonical_name, p.brand, p.category,
                       similarity(pa.alias_name, ?) as sim
                FROM products p
                JOIN product_aliases pa ON pa.product_id = p.id
                WHERE similarity(pa.alias_name, ?) > ?
                ORDER BY sim DESC
                LIMIT 1
                """,
                args = listOf(
                    normalizedName, normalizedName, threshold,
                    normalizedName, normalizedName, threshold
                )
            ) { rs ->
                if (rs.next()) Product(
                    id = UUID.fromString(rs.getString("id")),
                    ean = rs.getString("ean"),
                    canonicalName = rs.getString("canonical_name"),
                    brand = rs.getString("brand"),
                    category = rs.getString("category"),
                ) else null
            }
        }

    suspend fun createProduct(name: String, ean: String?, category: String?): UUID =
        newSuspendedTransaction {
            ProductsTable.insert {
                it[canonicalName] = name.uppercase().trim()
                it[this.ean] = ean
                it[this.category] = category
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }[ProductsTable.id]
        }

    suspend fun addAlias(productId: UUID, storeChain: String, aliasName: String) =
        newSuspendedTransaction {
            // Solo agregar si no existe ya
            val exists = ProductAliasesTable.select {
                (ProductAliasesTable.productId eq productId) and
                (ProductAliasesTable.storeChain eq storeChain) and
                (ProductAliasesTable.aliasName eq aliasName)
            }.any()
            if (!exists) {
                ProductAliasesTable.insert {
                    it[this.productId] = productId
                    it[this.storeChain] = storeChain
                    it[this.aliasName] = aliasName
                    it[createdAt] = Instant.now()
                }
            }
        }
}
```

### 3. ProductMatcher.kt

```kotlin
package com.receiptanalyzer.product

import java.util.UUID

class ProductMatcher(private val repository: ProductRepository) {

    /**
     * Dado un item de un ticket, retorna el ID del producto canónico.
     * Si no existe, lo crea y registra el alias.
     */
    suspend fun matchOrCreate(
        rawName: String,
        ean: String?,
        category: String?,
        storeChain: String,
    ): UUID {
        // 1. Match por EAN (más confiable)
        if (ean != null) {
            val byEan = repository.findByEan(ean)
            if (byEan != null) {
                // Registrar este nombre como alias si es nuevo
                repository.addAlias(byEan.id, storeChain, rawName)
                return byEan.id
            }
        }

        // 2. Fuzzy match por nombre
        val byName = repository.findByFuzzyName(rawName)
        if (byName != null) {
            repository.addAlias(byName.id, storeChain, rawName)
            if (ean != null) {
                // Actualizar el EAN si el producto no lo tenía
                // (esto requiere un método updateEan en el repository)
            }
            return byName.id
        }

        // 3. Crear producto nuevo
        val newId = repository.createProduct(rawName, ean, category)
        repository.addAlias(newId, storeChain, rawName)
        return newId
    }
}
```

### 4. Integrar ProductMatcher en ReceiptService

En `ReceiptService.kt`, después de guardar los items, hacer el matching:

```kotlin
// Después de repository.updateFromStructured(...)
val storeChain = structured.store.chainName
structured.items.forEach { item ->
    val receiptItemId = // obtener el id del item recién insertado
    val productId = productMatcher.matchOrCreate(
        rawName = item.rawName,
        ean = item.ean,
        category = item.category,
        storeChain = storeChain,
    )
    // Actualizar receipt_items.product_id
    receiptItemRepository.setProductId(receiptItemId, productId)
    // Registrar price_observation
    priceObservationRepository.insert(
        productId = productId,
        storeId = storeId ?: return@forEach,
        price = item.unitPrice,
        sourceId = receiptItemId,
    )
}
```

### 5. Store matching

En `ReceiptService.kt`, antes del matching de productos, resolver la tienda:

```kotlin
suspend fun resolveStore(structured: StructuredReceipt): UUID? {
    // Buscar por CUIT (más preciso)
    val cuit = structured.store.cuit
    if (cuit != null) {
        return storeRepository.findByCuit(cuit)?.id
            ?: storeRepository.create(
                chainName = structured.store.chainName,
                address = structured.store.address,
                cuit = cuit,
            )
    }
    return null
}
```

## Verificación

1. Subir el ticket de Carrefour.
2. Consultar la tabla `products` en Supabase → deben aparecer 6 productos nuevos.
3. Cada uno debe tener su EAN correspondiente.
4. Subir el mismo ticket de nuevo → no deben crearse productos duplicados.
5. Consultar `product_aliases` → los nombres del ticket de Carrefour deben estar registrados con `store_chain = 'Carrefour'`.
6. Consultar `price_observations` → 6 filas con los precios del ticket.

## Archivos que se crean/modifican
- `backend/src/main/kotlin/com/receiptanalyzer/product/ProductTable.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/product/ProductRepository.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/product/ProductMatcher.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/ReceiptService.kt` (modificar)
