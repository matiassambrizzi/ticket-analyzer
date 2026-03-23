# Plan 11 — Frontend: UI de Comparación de Precios

## Objetivo
Agregar a la página de detalle de un ticket la opción "Comparar precios" que muestra alternativas más baratas por producto y el ahorro total potencial.

## Pre-requisitos
- Plan 10 completado (endpoint de comparación)
- Plan 07 completado (página de detalle de ticket)

## Pasos

### 1. Agregar función en api.ts

```typescript
export async function compareReceipt(id: string) {
  const res = await fetch(`${PUBLIC_BACKEND_URL}/api/compare/receipt/${id}`, {
    method: 'POST',
    headers: await authHeaders(),
  });
  return res.json();
}
```

### 2. src/lib/components/PriceComparison.svelte

```svelte
<script lang="ts">
  export let comparison: {
    totalPaid: number;
    totalCheapest: number;
    potentialSavings: number;
    items: Array<{
      rawName: string;
      unitPrice: number;
      savings: number;
      alternatives: Array<{
        chainName: string;
        latestPrice: number;
        savingsPct: number;
        isPromo: boolean;
      }>;
    }>;
  };

  // Mostrar solo items donde hay ahorro real
  $: itemsWithSavings = comparison.items.filter(i => i.savings > 0);
</script>

<div class="space-y-4">
  <!-- Resumen de ahorro -->
  <div class="bg-green-50 border border-green-200 rounded-xl p-5">
    <div class="flex justify-between items-center">
      <div>
        <p class="text-green-800 font-semibold text-lg">
          Ahorro potencial: ${comparison.potentialSavings.toLocaleString('es-AR')}
        </p>
        <p class="text-green-600 text-sm mt-0.5">
          Comprando en las tiendas más baratas por cada producto
        </p>
      </div>
      <div class="text-right text-sm text-green-700">
        <p>Pagaste: ${comparison.totalPaid.toLocaleString('es-AR')}</p>
        <p>Mínimo posible: ${comparison.totalCheapest.toLocaleString('es-AR')}</p>
      </div>
    </div>
  </div>

  <!-- Items con alternativas más baratas -->
  {#if itemsWithSavings.length === 0}
    <p class="text-gray-400 text-center py-6">
      No hay datos de precios de otras tiendas para estos productos todavía.
    </p>
  {:else}
    <div class="space-y-3">
      {#each itemsWithSavings as item}
        <div class="bg-white border rounded-xl p-4">
          <div class="flex justify-between items-start mb-3">
            <p class="font-medium text-sm">{item.rawName}</p>
            <span class="text-green-600 font-semibold text-sm">
              -${item.savings.toLocaleString('es-AR')}
            </span>
          </div>
          <div class="space-y-1.5">
            {#each item.alternatives.slice(0, 3) as alt}
              <div class="flex justify-between items-center text-sm">
                <span class="text-gray-600">
                  {alt.chainName}
                  {#if alt.isPromo}
                    <span class="text-xs bg-orange-100 text-orange-700 px-1.5 py-0.5 rounded ml-1">Promo</span>
                  {/if}
                </span>
                <div class="flex items-center gap-2">
                  <span class="text-xs text-green-600">-{alt.savingsPct.toFixed(0)}%</span>
                  <span class="font-medium">${alt.latestPrice.toLocaleString('es-AR')}</span>
                </div>
              </div>
            {/each}
          </div>
        </div>
      {/each}
    </div>
  {/if}
</div>
```

### 3. Actualizar src/routes/receipts/[id]/+page.svelte

Agregar el botón "Comparar precios" y mostrar el componente:

```svelte
<script lang="ts">
  // ... imports existentes ...
  import PriceComparison from '$lib/components/PriceComparison.svelte';
  import { compareReceipt } from '$lib/api';

  let comparison: any = null;
  let comparing = false;

  async function loadComparison() {
    comparing = true;
    comparison = await compareReceipt($page.params.id);
    comparing = false;
  }
</script>

<!-- Después de la tabla de items, agregar: -->
<div>
  {#if !comparison}
    <button on:click={loadComparison} disabled={comparing}
      class="w-full bg-green-600 text-white py-3 rounded-xl font-medium hover:bg-green-700 disabled:opacity-50">
      {comparing ? 'Comparando precios...' : '¿Dónde conviene comprar estos productos?'}
    </button>
  {:else}
    <PriceComparison {comparison} />
  {/if}
</div>
```

## Verificación

1. Ir al detalle de un ticket con datos de comparación disponibles.
2. Clickear "¿Dónde conviene comprar estos productos?"
3. Ver el panel verde con el ahorro potencial y los productos más baratos en otras tiendas.
4. Si no hay datos de otras tiendas, ver el mensaje "No hay datos todavía".

## Archivos que se crean/modifican
- `frontend/src/lib/components/PriceComparison.svelte`
- `frontend/src/lib/api.ts` (agregar `compareReceipt`)
- `frontend/src/routes/receipts/[id]/+page.svelte` (agregar botón y componente)
