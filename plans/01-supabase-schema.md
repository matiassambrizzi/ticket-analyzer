# Plan 01 — Supabase: Proyecto + Schema

## Objetivo
Crear el proyecto Supabase y aplicar el schema inicial de la base de datos.

## Pre-requisitos
- Acceso a Supabase (MCP disponible)
- Ningún archivo de código previo requerido

## Pasos

### 1. Crear el proyecto Supabase
Usar el MCP de Supabase para:
1. Listar organizaciones disponibles (`list_organizations`)
2. Crear un nuevo proyecto llamado `receipt-analyzer` (`create_project`)
3. Anotar la URL del proyecto y la anon key pública (`get_project_url`, `get_publishable_keys`)

### 2. Aplicar Migration 001 — Schema inicial

Crear las siguientes tablas usando `apply_migration`:

```sql
create extension if not exists "pgcrypto";
create extension if not exists "pg_trgm";

-- STORES
create table stores (
  id uuid primary key default gen_random_uuid(),
  chain_name text not null,
  branch_name text,
  address text,
  city text default 'CABA',
  cuit text,
  created_at timestamptz not null default now()
);
create unique index stores_cuit_idx on stores(cuit) where cuit is not null;

-- PRODUCTS
create table products (
  id uuid primary key default gen_random_uuid(),
  ean text,
  canonical_name text not null,
  brand text,
  category text,
  unit text,
  unit_quantity numeric(10,3),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index products_ean_idx on products(ean) where ean is not null;
create index products_name_trgm_idx on products using gin(canonical_name gin_trgm_ops);

-- PRODUCT ALIASES
create table product_aliases (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references products(id) on delete cascade,
  store_chain text not null,
  alias_name text not null,
  created_at timestamptz not null default now()
);
create index product_aliases_name_idx on product_aliases using gin(alias_name gin_trgm_ops);

-- USER PROFILES
create table user_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  preferred_stores uuid[],
  created_at timestamptz not null default now()
);

-- RECEIPTS
create table receipts (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  store_id uuid references stores(id),
  receipt_date timestamptz,
  receipt_number text,
  invoice_type text,
  cae text,
  subtotal numeric(12,2),
  total_discount numeric(12,2) default 0,
  total numeric(12,2) not null,
  iva_total numeric(12,2),
  payment_method text,
  raw_text text,
  file_path text,
  processing_status text not null default 'pending',
  processing_error text,
  created_at timestamptz not null default now()
);
create index receipts_user_id_idx on receipts(user_id);
create index receipts_store_id_idx on receipts(store_id);
create index receipts_date_idx on receipts(receipt_date);

-- RECEIPT ITEMS
create table receipt_items (
  id uuid primary key default gen_random_uuid(),
  receipt_id uuid not null references receipts(id) on delete cascade,
  product_id uuid references products(id),
  raw_name text not null,
  ean text,
  category text,
  quantity numeric(10,3) not null default 1,
  unit_price numeric(12,2) not null,
  iva_pct numeric(5,2),
  line_total numeric(12,2) not null,
  discount numeric(12,2) default 0,
  discount_label text,
  net_total numeric(12,2) not null,
  sort_order integer,
  created_at timestamptz not null default now()
);
create index receipt_items_receipt_id_idx on receipt_items(receipt_id);
create index receipt_items_product_id_idx on receipt_items(product_id);
create index receipt_items_ean_idx on receipt_items(ean);

-- PRICE OBSERVATIONS
create table price_observations (
  id uuid primary key default gen_random_uuid(),
  product_id uuid not null references products(id) on delete cascade,
  store_id uuid not null references stores(id) on delete cascade,
  price numeric(12,2) not null,
  source text not null,
  source_id uuid,
  observed_at timestamptz not null default now(),
  is_promo boolean default false,
  promo_label text
);
create index price_obs_product_store_idx on price_observations(product_id, store_id);
create index price_obs_observed_at_idx on price_observations(observed_at desc);

-- SCRAPE RUNS
create table scrape_runs (
  id uuid primary key default gen_random_uuid(),
  store_chain text not null,
  category text,
  products_found integer default 0,
  prices_updated integer default 0,
  status text not null default 'running',
  error text,
  started_at timestamptz not null default now(),
  finished_at timestamptz
);
```

### 3. Aplicar Migration 002 — RLS Policies

```sql
alter table user_profiles enable row level security;
alter table receipts enable row level security;
alter table receipt_items enable row level security;
alter table products enable row level security;
alter table stores enable row level security;
alter table price_observations enable row level security;

create policy "users_own_profile" on user_profiles
  for all using (auth.uid() = id);

create policy "users_own_receipts" on receipts
  for all using (auth.uid() = user_id);

create policy "users_own_receipt_items" on receipt_items
  for all using (
    receipt_id in (select id from receipts where user_id = auth.uid())
  );

create policy "products_public_read" on products for select using (true);
create policy "stores_public_read" on stores for select using (true);
create policy "prices_public_read" on price_observations for select using (true);
```

### 4. Aplicar Migration 003 — Funciones SQL

```sql
create or replace function get_price_comparison(p_product_id uuid)
returns table (
  store_id uuid,
  chain_name text,
  latest_price numeric,
  observed_at timestamptz,
  is_promo boolean
) language sql stable as $$
  select distinct on (po.store_id)
    po.store_id,
    s.chain_name,
    po.price,
    po.observed_at,
    po.is_promo
  from price_observations po
  join stores s on s.id = po.store_id
  where po.product_id = p_product_id
    and po.observed_at > now() - interval '30 days'
  order by po.store_id, po.observed_at desc;
$$;

create or replace function get_spending_by_category(
  p_user_id uuid,
  p_from timestamptz,
  p_to timestamptz
)
returns table (
  category text,
  total_spent numeric,
  item_count bigint
) language sql stable as $$
  select
    ri.category,
    sum(ri.net_total),
    count(*)
  from receipt_items ri
  join receipts r on r.id = ri.receipt_id
  where r.user_id = p_user_id
    and r.receipt_date between p_from and p_to
  group by ri.category
  order by total_spent desc;
$$;
```

### 5. Seed — Tiendas conocidas

```sql
insert into stores (chain_name, branch_name, address, city, cuit) values
  ('Carrefour', 'Alvarez Jonte', 'AV. ALVAREZ JONTE 4872', 'CABA', '30-68731043-4');
```

### 6. Crear Storage Bucket

Crear el bucket `receipts` en Supabase Storage con acceso privado (solo el dueño del archivo puede acceder).

### 7. Guardar configuración

Crear el archivo `backend/.env.example` con las variables necesarias:
```
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_ANON_KEY=eyJ...
SUPABASE_JWT_SECRET=...
DATABASE_URL=postgresql://postgres:password@db.xxxxx.supabase.co:5432/postgres
CLAUDE_API_KEY=sk-ant-...
```

## Verificación

1. Ejecutar `list_tables` en el MCP de Supabase → deben aparecer las 8 tablas.
2. Verificar que la tienda Carrefour de ejemplo fue insertada.
3. Confirmar que el bucket `receipts` existe en Storage.

## Archivos que se crean
- `backend/.env.example`
- (Migraciones aplicadas directamente en Supabase, no requieren archivos locales por ahora)
