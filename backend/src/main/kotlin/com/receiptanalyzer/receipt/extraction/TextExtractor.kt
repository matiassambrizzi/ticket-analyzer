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
