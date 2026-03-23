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
