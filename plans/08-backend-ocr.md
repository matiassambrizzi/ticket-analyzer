# Plan 08 — Backend: OCR para Tickets en Imagen

## Objetivo
Agregar soporte para tickets en imagen (JPG/PNG) y PDFs escaneados usando Tesseract OCR via Tess4J.

## Pre-requisitos
- Plan 03 completado (PdfTextExtractor y ExtractorFactory)
- Tesseract instalado en el sistema (`tesseract --version`)
- Paquete de idioma español: `tessdata/spa.traineddata`

## Instalación del sistema (Linux)

```bash
# Arch/Artix
sudo pacman -S tesseract tesseract-data-spa

# Ubuntu/Debian
sudo apt install tesseract-ocr tesseract-ocr-spa
```

## Pasos

### 1. Agregar Tess4J en build.gradle.kts

```kotlin
implementation("net.sourceforge.tess4j:tess4j:5.13.0")
```

### 2. OcrExtractor.kt

`backend/src/main/kotlin/com/receiptanalyzer/receipt/extraction/OcrExtractor.kt`:

```kotlin
package com.receiptanalyzer.receipt.extraction

import net.sourceforge.tess4j.Tesseract
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class OcrExtractor(
    private val tessDataPath: String = "/usr/share/tessdata",
    private val language: String = "spa",
) : TextExtractor {

    private fun buildTesseract(): Tesseract = Tesseract().apply {
        setDatapath(tessDataPath)
        setLanguage(language)
        // Modo de segmentación de página: auto con OSD
        setPageSegMode(1)
        // Motor neuronal LSTM
        setOcrEngineMode(1)
    }

    override fun extract(bytes: ByteArray, mimeType: String): ExtractionResult {
        val image: BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))
            ?: return ExtractionResult("", ExtractionMethod.UNKNOWN, 1)
        val tesseract = buildTesseract()
        val text = tesseract.doOCR(image).trim()
        return ExtractionResult(
            text = text,
            method = ExtractionMethod.OCR,
            pageCount = 1,
        )
    }
}
```

### 3. Actualizar ExtractorFactory para usar OCR

```kotlin
class ExtractorFactory(
    private val pdfExtractor: PdfTextExtractor = PdfTextExtractor(),
    private val ocrExtractor: OcrExtractor = OcrExtractor(),
) {
    fun extract(bytes: ByteArray, mimeType: String): ExtractionResult {
        return when {
            mimeType == "application/pdf" -> {
                val result = pdfExtractor.extract(bytes, mimeType)
                // Fallback a OCR si el PDF no tiene texto (escaneado)
                if (result.method == ExtractionMethod.UNKNOWN) {
                    extractPdfPagesAsImages(bytes)
                } else {
                    result
                }
            }
            mimeType.startsWith("image/") -> ocrExtractor.extract(bytes, mimeType)
            else -> ExtractionResult("", ExtractionMethod.UNKNOWN, 0)
        }
    }

    private fun extractPdfPagesAsImages(bytes: ByteArray): ExtractionResult {
        // Renderizar el PDF como imagen con PDFBox y pasarla al OCR
        val document = org.apache.pdfbox.Loader.loadPDF(bytes)
        val renderer = org.apache.pdfbox.rendering.PDFRenderer(document)
        val texts = (0 until document.numberOfPages).map { pageIndex ->
            val image = renderer.renderImageWithDPI(pageIndex, 300f)
            val imageBytes = java.io.ByteArrayOutputStream().also {
                javax.imageio.ImageIO.write(image, "PNG", it)
            }.toByteArray()
            ocrExtractor.extract(imageBytes, "image/png").text
        }
        document.close()
        return ExtractionResult(
            text = texts.joinToString("\n"),
            method = ExtractionMethod.OCR,
            pageCount = texts.size,
        )
    }
}
```

### 4. Test con imagen de ticket

`backend/src/test/kotlin/com/receiptanalyzer/receipt/extraction/OcrExtractorTest.kt`:

```kotlin
class OcrExtractorTest {
    private val extractor = OcrExtractor()

    @Test
    fun `extracts text from receipt image`() {
        // Requiere una imagen de ticket de prueba en test/resources/receipts/
        val imageBytes = javaClass.getResourceAsStream("/receipts/receipt_sample.png")
            ?: return // skip si no hay imagen de test
        val result = extractor.extract(imageBytes.readBytes(), "image/png")
        assertEquals(ExtractionMethod.OCR, result.method)
        assertTrue(result.text.isNotBlank())
    }
}
```

### 5. Configurar la ruta de tessdata vía variable de entorno

En `AppConfig.kt`:

```kotlin
data class AppConfig(
    // ... campos existentes ...
    val tessDataPath: String = "/usr/share/tessdata",
)

// En load():
tessDataPath = env.get("TESSERACT_DATA_PATH") ?: "/usr/share/tessdata",
```

En `.env.example`:
```
TESSERACT_DATA_PATH=/usr/share/tessdata
```

## Verificación

1. `./gradlew test --tests "*.OcrExtractorTest"` → pasa (si hay imagen de prueba).
2. Subir una foto de un ticket via la API → debe completar el pipeline y extraer productos.
3. Subir un PDF escaneado (sin texto) → debe hacer fallback a OCR.

## Archivos que se crean/modifican
- `backend/build.gradle.kts` (agregar Tess4J)
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/extraction/OcrExtractor.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/receipt/extraction/ExtractorFactory.kt` (modificar)
- `backend/src/main/kotlin/com/receiptanalyzer/config/AppConfig.kt` (agregar tessDataPath)
- `backend/src/test/kotlin/com/receiptanalyzer/receipt/extraction/OcrExtractorTest.kt`
