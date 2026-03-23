# Plan 03 — Backend: Extracción de Texto de PDFs

## Objetivo
Implementar la extracción de texto de PDFs con Apache PDFBox. Soportar PDFs digitales (como el de Carrefour) y detectar cuando el PDF es escaneado (sin texto) para hacer fallback.

## Pre-requisitos
- Plan 02 completado (backend funcionando)
- Archivo de prueba: `receipt_1774179045000.pdf` (en la raíz del proyecto)

## Pasos

### 1. Agregar dependencia PDFBox en build.gradle.kts

```kotlin
// PDF extraction
implementation("org.apache.pdfbox:pdfbox:3.0.3")
```

### 2. Interfaz TextExtractor

`backend/src/main/kotlin/com/receiptanalyzer/receipt/extraction/TextExtractor.kt`:

```kotlin
package com.receiptanalyzer.receipt.extraction

data class ExtractionResult(
    val text: String,
    val method: ExtractionMethod,
    val pageCount: Int,
)

enum class ExtractionMethod { PDF_TEXT, OCR, UNKNOWN }

interface TextExtractor {
    fun extract(bytes: ByteArray, mimeType: String): ExtractionResult
}
```

### 3. PdfTextExtractor

`backend/src/main/kotlin/com/receiptanalyzer/receipt/extraction/PdfTextExtractor.kt`:

```kotlin
package com.receiptanalyzer.receipt.extraction

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

class PdfTextExtractor : TextExtractor {

    private val stripper = PDFTextStripper().apply {
        sortByPosition = true
        addMoreFormatting = false
    }

    override fun extract(bytes: ByteArray, mimeType: String): ExtractionResult {
        val document = Loader.loadPDF(bytes)
        return document.use { doc ->
            val text = stripper.getText(doc).trim()
            ExtractionResult(
                text = text,
                method = if (text.length > MIN_TEXT_LENGTH) ExtractionMethod.PDF_TEXT else ExtractionMethod.UNKNOWN,
                pageCount = doc.numberOfPages,
            )
        }
    }

    companion object {
        // Si el PDF tiene menos de 50 caracteres de texto, probablemente es escaneado
        private const val MIN_TEXT_LENGTH = 50
    }
}
```

### 4. Test con el PDF de Carrefour

`backend/src/test/kotlin/com/receiptanalyzer/receipt/extraction/PdfTextExtractorTest.kt`:

```kotlin
package com.receiptanalyzer.receipt.extraction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfTextExtractorTest {

    private val extractor = PdfTextExtractor()

    @Test
    fun `extracts text from digital Carrefour PDF`() {
        val pdfBytes = javaClass.getResourceAsStream("/receipts/receipt_sample.pdf")!!
            .readBytes()

        val result = extractor.extract(pdfBytes, "application/pdf")

        assertEquals(ExtractionMethod.PDF_TEXT, result.method)
        // Verificar que los datos clave están en el texto extraído
        assertTrue(result.text.contains("Carrefour"), "Debe contener el nombre de la tienda")
        assertTrue(result.text.contains("YERBA MATE AMANDA"), "Debe contener productos")
        assertTrue(result.text.contains("4650"), "Debe contener precios")
        assertTrue(result.text.contains("26886"), "Debe contener el total")
        assertTrue(result.text.contains("7792710000175"), "Debe contener EAN barcodes")
        assertTrue(result.text.contains("22/03/26"), "Debe contener la fecha")
    }

    @Test
    fun `detects scanned PDF with no extractable text`() {
        // Un PDF con solo imágenes tendrá texto vacío o muy corto
        val emptyPdfBytes = createEmptyPdf()
        val result = extractor.extract(emptyPdfBytes, "application/pdf")
        assertEquals(ExtractionMethod.UNKNOWN, result.method)
    }
}
```

> **Nota**: Copiar `receipt_1774179045000.pdf` a `backend/src/test/resources/receipts/receipt_sample.pdf` para usarlo en los tests.

### 5. ExtractorFactory (para elegir extractor según tipo de archivo)

`backend/src/main/kotlin/com/receiptanalyzer/receipt/extraction/ExtractorFactory.kt`:

```kotlin
package com.receiptanalyzer.receipt.extraction

class ExtractorFactory(
    private val pdfExtractor: PdfTextExtractor = PdfTextExtractor(),
    // OcrExtractor se agrega en Plan 08
) {
    fun extract(bytes: ByteArray, mimeType: String): ExtractionResult {
        return when {
            mimeType == "application/pdf" -> {
                val result = pdfExtractor.extract(bytes, mimeType)
                // Si el PDF no tiene texto, en Plan 08 habrá fallback a OCR
                result
            }
            mimeType.startsWith("image/") -> {
                // Plan 08: OCR
                ExtractionResult("", ExtractionMethod.UNKNOWN, 1)
            }
            else -> ExtractionResult("", ExtractionMethod.UNKNOWN, 0)
        }
    }
}
```

## Verificación

1. Copiar el PDF de muestra: `cp receipt_1774179045000.pdf backend/src/test/resources/receipts/receipt_sample.pdf`
2. `./gradlew test --tests "*.PdfTextExtractorTest"` → todos los tests deben pasar.
3. Inspeccionar manualmente el texto extraído (agregar un `println(result.text)` al test) y verificar que el formato coincide con lo que se ve en el PDF original.

## Archivos que se crean/modifican
- `backend/build.gradle.kts` (agregar PDFBox)
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/extraction/TextExtractor.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/extraction/PdfTextExtractor.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/extraction/ExtractorFactory.kt`
- `backend/src/test/kotlin/com/receiptanalyzer/receipt/extraction/PdfTextExtractorTest.kt`
- `backend/src/test/resources/receipts/receipt_sample.pdf` (copiar del archivo existente)
