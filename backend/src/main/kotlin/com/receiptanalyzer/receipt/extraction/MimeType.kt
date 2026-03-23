package com.receiptanalyzer.receipt.extraction

sealed class MimeType {
    data object Pdf : MimeType()
    data class Image(val subtype: String) : MimeType()
    data class Unknown(val raw: String) : MimeType()

    companion object {
        fun of(raw: String): MimeType = when {
            raw == "application/pdf" -> Pdf
            raw.startsWith("image/") -> Image(raw.removePrefix("image/"))
            else -> Unknown(raw)
        }
    }
}
