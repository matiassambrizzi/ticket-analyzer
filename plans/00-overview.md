# Receipt Analyzer — Índice de Planes

Cada plan es autocontenido y ejecutable de forma independiente. Seguir el orden.

## Stack
- Backend: Kotlin + Ktor
- Frontend: SvelteKit + Tailwind CSS
- DB / Auth / Storage: Supabase
- Extracción: PDFBox (PDFs) + Tesseract (imágenes) + Claude Haiku API (estructurar)
- Scraping: Ktor HttpClient + Jsoup

## Planes

| # | Archivo | Descripción | Estado |
|---|---------|-------------|--------|
| 01 | [01-supabase-schema.md](01-supabase-schema.md) | Crear proyecto Supabase + schema inicial | ✅ Realizado |
| 02 | [02-backend-scaffold.md](02-backend-scaffold.md) | Scaffolding Kotlin/Ktor con Gradle | ✅ Realizado |
| 03 | [03-backend-pdf-extraction.md](03-backend-pdf-extraction.md) | Extracción de texto de PDFs con PDFBox | ✅ Realizado |
| 04 | [04-backend-llm-structuring.md](04-backend-llm-structuring.md) | Integración Claude API para estructurar datos | ⬜ Pendiente |
| 05 | [05-backend-receipt-api.md](05-backend-receipt-api.md) | Endpoints REST para subir y consultar tickets | ⬜ Pendiente |
| 06 | [06-frontend-scaffold.md](06-frontend-scaffold.md) | Scaffolding SvelteKit + Auth + Tailwind | ⬜ Pendiente |
| 07 | [07-frontend-upload-and-detail.md](07-frontend-upload-and-detail.md) | Páginas de upload y detalle de ticket | ⬜ Pendiente |
| 08 | [08-backend-ocr.md](08-backend-ocr.md) | OCR con Tesseract para tickets en imagen | ⬜ Pendiente |
| 09 | [09-backend-product-matching.md](09-backend-product-matching.md) | Matching de productos por EAN y fuzzy search | ⬜ Pendiente |
| 10 | [10-backend-price-comparison.md](10-backend-price-comparison.md) | Comparación de precios entre tiendas | ⬜ Pendiente |
| 11 | [11-frontend-price-comparison.md](11-frontend-price-comparison.md) | UI de comparación de precios y ahorro | ⬜ Pendiente |
| 12 | [12-backend-scraper.md](12-backend-scraper.md) | Scraper de precios (Carrefour, Coto, Jumbo, Día) | ⬜ Pendiente |
| 13 | [13-frontend-dashboard.md](13-frontend-dashboard.md) | Dashboard con gráficos y tendencias de gasto | ⬜ Pendiente |
