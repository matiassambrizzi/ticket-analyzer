package com.receiptanalyzer.receipt.extraction

import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream
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
        assertTrue(result.text.contains("FACTURA B"), "Debe contener el tipo de factura")
        assertTrue(result.text.contains("YERBA MATE AMANDA"), "Debe contener productos")
        assertTrue(result.text.contains("4650"), "Debe contener precios")
        assertTrue(result.text.contains("26886"), "Debe contener el total")
        assertTrue(result.text.contains("7792710000175"), "Debe contener EAN barcodes")
        assertTrue(result.text.contains("22/03/26"), "Debe contener la fecha")
    }

    @Test
    fun `detects scanned PDF with no extractable text`() {
        val emptyPdfBytes = createEmptyPdf()
        val result = extractor.extract(emptyPdfBytes, "application/pdf")
        assertEquals(ExtractionMethod.UNKNOWN, result.method)
    }

    private fun createEmptyPdf(): ByteArray {
        val doc = PDDocument()
        doc.addPage(org.apache.pdfbox.pdmodel.PDPage())
        val out = ByteArrayOutputStream()
        doc.use { it.save(out) }
        return out.toByteArray()
    }
}
