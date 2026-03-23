package com.receiptanalyzer.receipt.extraction

class ExtractorFactory(
    private val pdfExtractor: PdfTextExtractor = PdfTextExtractor(),
    // OcrExtractor se agrega en Plan 08
) : TextExtractor {
    override fun extract(bytes: ByteArray, mimeType: MimeType): ExtractionResult {
        return when (mimeType) {
            is MimeType.Pdf -> {
                val result = pdfExtractor.extract(bytes, mimeType)
                // Si el PDF no tiene texto, en Plan 08 habrá fallback a OCR
                result
            }
            is MimeType.Image -> {
                // Plan 08: OCR
                ExtractionResult("", ExtractionMethod.UNKNOWN, 1)
            }
            is MimeType.Unknown -> ExtractionResult("", ExtractionMethod.UNKNOWN, 0)
        }
    }
}
