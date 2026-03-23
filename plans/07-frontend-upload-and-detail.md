# Plan 07 — Frontend: Upload y Detalle de Ticket

## Objetivo
Implementar las páginas de upload de ticket, lista de tickets y detalle con todos los items extraídos. Al finalizar este plan el flujo MVP completo es usable desde el browser.

## Pre-requisitos
- Plan 05 completado (backend API funcionando)
- Plan 06 completado (frontend con auth)

## Pasos

### 1. src/routes/receipts/+page.svelte (lista + upload)

```svelte
<script lang="ts">
  import { onMount } from 'svelte';
  import { getReceipts, uploadReceipt } from '$lib/api';

  let receipts: any[] = [];
  let uploading = false;
  let dragOver = false;

  onMount(async () => {
    receipts = await getReceipts();
  });

  async function handleFile(file: File) {
    uploading = true;
    const { receiptId } = await uploadReceipt(file);
    // Polling hasta que el status sea completed o failed
    await pollStatus(receiptId);
    receipts = await getReceipts();
    uploading = false;
  }

  async function pollStatus(id: string) {
    const { getReceiptStatus } = await import('$lib/api');
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 2000));
      const { status } = await getReceiptStatus(id);
      if (status === 'completed' || status === 'failed') return;
    }
  }

  function onDrop(e: DragEvent) {
    dragOver = false;
    const file = e.dataTransfer?.files[0];
    if (file) handleFile(file);
  }
</script>

<div class="space-y-6">
  <div class="flex justify-between items-center">
    <h1 class="text-xl font-semibold">Mis tickets</h1>
  </div>

  <!-- Drop zone -->
  <div
    class="border-2 border-dashed rounded-xl p-10 text-center transition-colors"
    class:border-blue-400={dragOver}
    class:bg-blue-50={dragOver}
    class:border-gray-300={!dragOver}
    on:dragover|preventDefault={() => dragOver = true}
    on:dragleave={() => dragOver = false}
    on:drop|preventDefault={onDrop}
    role="button"
    tabindex="0"
  >
    {#if uploading}
      <p class="text-blue-600 font-medium">Procesando ticket...</p>
      <p class="text-sm text-gray-500 mt-1">Extrayendo y analizando productos</p>
    {:else}
      <p class="text-gray-600">Arrastrá un PDF o imagen acá</p>
      <p class="text-sm text-gray-400 mt-1">o</p>
      <label class="mt-2 inline-block cursor-pointer">
        <span class="bg-blue-600 text-white px-4 py-2 rounded-lg text-sm hover:bg-blue-700">
          Elegir archivo
        </span>
        <input type="file" accept=".pdf,image/*" class="hidden"
          on:change={(e) => { const f = e.currentTarget.files?.[0]; if (f) handleFile(f); }} />
      </label>
    {/if}
  </div>

  <!-- Lista de tickets -->
  {#if receipts.length === 0}
    <p class="text-gray-400 text-center py-8">No hay tickets cargados aún</p>
  {:else}
    <div class="space-y-3">
      {#each receipts as r}
        <a href="/receipts/{r.id}"
          class="block bg-white rounded-xl border p-4 hover:shadow-sm transition-shadow">
          <div class="flex justify-between items-start">
            <div>
              <p class="font-medium">{r.storeName ?? 'Tienda desconocida'}</p>
              <p class="text-sm text-gray-500 mt-0.5">
                {r.receiptDate ? new Date(r.receiptDate).toLocaleDateString('es-AR') : '—'}
              </p>
            </div>
            <div class="text-right">
              <p class="font-semibold">${r.total?.toLocaleString('es-AR')}</p>
              <span class="text-xs px-2 py-0.5 rounded-full mt-1 inline-block"
                class:bg-green-100={r.processingStatus === 'completed'}
                class:text-green-700={r.processingStatus === 'completed'}
                class:bg-yellow-100={r.processingStatus !== 'completed' && r.processingStatus !== 'failed'}
                class:text-yellow-700={r.processingStatus !== 'completed' && r.processingStatus !== 'failed'}
                class:bg-red-100={r.processingStatus === 'failed'}
                class:text-red-700={r.processingStatus === 'failed'}>
                {r.processingStatus}
              </span>
            </div>
          </div>
        </a>
      {/each}
    </div>
  {/if}
</div>
```

### 2. src/routes/receipts/[id]/+page.svelte (detalle)

```svelte
<script lang="ts">
  import { page } from '$app/stores';
  import { onMount } from 'svelte';
  import { getReceipt } from '$lib/api';

  let receipt: any = null;

  onMount(async () => {
    receipt = await getReceipt($page.params.id);
  });
</script>

{#if !receipt}
  <p class="text-gray-400">Cargando...</p>
{:else}
  <div class="space-y-6">
    <div class="flex items-center gap-3">
      <a href="/receipts" class="text-gray-400 hover:text-gray-600">← Volver</a>
      <h1 class="text-xl font-semibold">{receipt.storeName ?? 'Ticket'}</h1>
    </div>

    <!-- Cabecera del ticket -->
    <div class="bg-white rounded-xl border p-5 grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
      <div>
        <p class="text-gray-400">Fecha</p>
        <p class="font-medium">{receipt.receiptDate ? new Date(receipt.receiptDate).toLocaleDateString('es-AR') : '—'}</p>
      </div>
      <div>
        <p class="text-gray-400">Total</p>
        <p class="font-medium text-lg">${receipt.total?.toLocaleString('es-AR')}</p>
      </div>
      <div>
        <p class="text-gray-400">Descuentos</p>
        <p class="font-medium text-green-600">-${receipt.totalDiscount?.toLocaleString('es-AR')}</p>
      </div>
      <div>
        <p class="text-gray-400">Pago</p>
        <p class="font-medium">{receipt.paymentMethod ?? '—'}</p>
      </div>
    </div>

    <!-- Tabla de items -->
    <div class="bg-white rounded-xl border overflow-hidden">
      <table class="w-full text-sm">
        <thead class="bg-gray-50 text-gray-500 text-xs">
          <tr>
            <th class="text-left px-4 py-3">Producto</th>
            <th class="text-right px-4 py-3">Cant.</th>
            <th class="text-right px-4 py-3">Precio unit.</th>
            <th class="text-right px-4 py-3">Descuento</th>
            <th class="text-right px-4 py-3">Total</th>
          </tr>
        </thead>
        <tbody class="divide-y">
          {#each receipt.items ?? [] as item}
            <tr class="hover:bg-gray-50">
              <td class="px-4 py-3">
                <p>{item.rawName}</p>
                {#if item.category}
                  <span class="text-xs text-gray-400">{item.category}</span>
                {/if}
              </td>
              <td class="px-4 py-3 text-right text-gray-600">{item.quantity}</td>
              <td class="px-4 py-3 text-right">${item.unitPrice?.toLocaleString('es-AR')}</td>
              <td class="px-4 py-3 text-right text-green-600">
                {item.discount > 0 ? `-$${item.discount?.toLocaleString('es-AR')}` : '—'}
              </td>
              <td class="px-4 py-3 text-right font-medium">${item.netTotal?.toLocaleString('es-AR')}</td>
            </tr>
          {/each}
        </tbody>
        <tfoot class="bg-gray-50">
          <tr>
            <td colspan="4" class="px-4 py-3 text-right font-semibold">Total</td>
            <td class="px-4 py-3 text-right font-bold">${receipt.total?.toLocaleString('es-AR')}</td>
          </tr>
        </tfoot>
      </table>
    </div>
  </div>
{/if}
```

## Verificación

1. Hacer login, ir a `/receipts`
2. Arrastrar `receipt_1774179045000.pdf` al drop zone
3. Ver el spinner de "Procesando..."
4. Al completarse, aparece en la lista con Carrefour, fecha 22/03/2026 y total $26.886,10
5. Clickear el ticket → ver la tabla con los 6 productos, cantidades, precios y descuentos

## Archivos que se crean
- `frontend/src/routes/receipts/+page.svelte` (reemplaza el placeholder)
- `frontend/src/routes/receipts/[id]/+page.svelte`
