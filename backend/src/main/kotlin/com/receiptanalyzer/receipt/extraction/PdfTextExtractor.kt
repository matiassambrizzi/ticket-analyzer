package com.receiptanalyzer.receipt.extraction

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

class PdfTextExtractor : TextExtractor {

    override fun extract(bytes: ByteArray, mimeType: MimeType): ExtractionResult {
        val stripper = PDFTextStripper().apply {
            sortByPosition = true
            addMoreFormatting = false
        }
        val document = Loader.loadPDF(bytes)
        return document.use { doc ->
            val text = stripper.getText(doc).replace('\u00A0', ' ').trim()
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
