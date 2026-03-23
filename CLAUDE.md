# Receipt Analyzer

App para analizar tickets de supermercados argentinos. Los usuarios suben tickets (PDF o foto), el sistema extrae los productos y precios, y ayuda a ahorrar comparando precios entre tiendas.

## Stack

| Capa | Tecnología |
|------|-----------|
| Backend | Kotlin + Ktor |
| Frontend | SvelteKit + Tailwind CSS |
| Base de datos | Supabase (PostgreSQL) |
| Auth | Supabase Auth (JWT) |
| Storage | Supabase Storage |
| Extracción PDF | Apache PDFBox |
| OCR (imágenes) | Tesseract / Tess4J |
| Estructuración | Claude API (Haiku) |
| Scraping | Ktor HttpClient + Jsoup |
| Deploy frontend | Vercel |
| Deploy backend | Railway |

## Estructura del proyecto

```
receipt-analyzer/
├── backend/                    # Kotlin + Ktor
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/receiptanalyzer/
│       ├── Application.kt
│       ├── config/             # AppConfig, DatabaseConfig, Routing
│       ├── auth/               # JWT validation (Supabase)
│       ├── receipt/            # Pipeline principal
│       │   ├── extraction/     # PDFBox + Tesseract
│       │   ├── structuring/    # Claude API
│       │   └── model/          # Receipt, ReceiptItem
│       ├── product/            # ProductMatcher (EAN + fuzzy)
│       ├── price/              # Comparación de precios
│       ├── scraper/            # Scrapers por cadena
│       └── dashboard/          # Aggregaciones
├── frontend/                   # SvelteKit
│   └── src/
│       ├── lib/
│       │   ├── supabase.ts
│       │   ├── api.ts
│       │   ├── stores/
│       │   └── components/
│       └── routes/
│           ├── +page.svelte    # Dashboard
│           ├── login/
│           ├── receipts/       # Lista, upload, detalle
│           └── compare/
├── supabase/
│   └── migrations/             # Schema SQL
└── plans/                      # Planes de implementación por pasos
    ├── 00-overview.md
    ├── 01-supabase-schema.md
    └── ...
```

## Arquitectura: Pipeline de procesamiento de tickets

```
Usuario sube PDF/imagen
        │
        ▼
Supabase Storage (guardar archivo)
        │
        ▼
ReceiptService.processAsync()
        │
        ├─▶ PdfTextExtractor (PDFBox) — PDFs digitales
        │         │ (sin texto → fallback)
        │         ▼
        └─▶ OcrExtractor (Tesseract) — imágenes y PDFs escaneados
                  │
                  ▼
           LlmStructurer (Claude Haiku API)
           → JSON estructurado: tienda, fecha, items, totales
                  │
                  ▼
           ProductMatcher
           → EAN lookup (exacto) → fuzzy pg_trgm (fallback) → crear nuevo
                  │
                  ▼
           DB: receipts + receipt_items + products + price_observations
```

## Base de datos (tablas principales)

| Tabla | Descripción |
|-------|-------------|
| `stores` | Cadenas y sucursales de supermercados. Unique index en CUIT. |
| `products` | Productos canónicos deduplicados. EAN como clave natural. Índice `pg_trgm` para fuzzy search. |
| `product_aliases` | Nombres alternativos del mismo producto en distintas cadenas. |
| `user_profiles` | Extiende `auth.users`. |
| `receipts` | Tickets subidos. Campo `processing_status`: pending → extracting → structuring → completed/failed. |
| `receipt_items` | Líneas del ticket vinculadas a `products`. |
| `price_observations` | Cada avistamiento de precio (source: 'receipt' o 'scraper'). Historial completo. |
| `scrape_runs` | Auditoría de corridas de scraping. |

## Variables de entorno

```bash
# Backend (.env)
DATABASE_URL=postgresql://postgres:password@db.xxxxx.supabase.co:5432/postgres
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_JWT_SECRET=...
CLAUDE_API_KEY=sk-ant-...
TESSERACT_DATA_PATH=/usr/share/tessdata   # o /usr/share/tesseract-ocr/5/tessdata

# Frontend (.env)
PUBLIC_SUPABASE_URL=https://xxxxx.supabase.co
PUBLIC_SUPABASE_ANON_KEY=eyJ...
PUBLIC_BACKEND_URL=http://localhost:8080   # en prod: URL de Railway
```

## Comandos útiles

```bash
# Backend
cd backend
./gradlew build
./gradlew run
./gradlew test

# Frontend
cd frontend
npm install
npm run dev
npm run build

# Tesseract (Arch/Artix)
sudo pacman -S tesseract tesseract-data-spa
```

## Planes de implementación

Los planes están en `plans/`. Ejecutar en orden. Cada plan es autocontenido.

Ver `plans/00-overview.md` para el índice completo.

## Supermercados soportados para scraping

- Carrefour (carrefour.com.ar) — REST API
- Coto (cotodigital3.com.ar) — HTML scraping
- Jumbo (jumbo.com.ar) — REST API (Vtex)
- Día (diaonline.supermercadosdia.com.ar) — REST API

## Consideraciones de costo

- Claude Haiku: ~$0.003-0.005 por ticket analizado
- Scraping: sin costo directo, pero requiere mantenimiento
- Supabase: free tier suficiente para MVP (500MB DB, 1GB Storage)
- Railway: ~$5/mes para backend Kotlin
- Vercel: free tier para SvelteKit
