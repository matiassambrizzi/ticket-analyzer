# Plan 13 — Frontend: Dashboard con Gráficos

## Objetivo
Implementar el dashboard principal con gráficos de gasto por categoría, tendencia mensual y métricas de ahorro.

## Pre-requisitos
- Plan 07 completado (página de detalle)
- Endpoint `GET /api/dashboard/summary` y `GET /api/dashboard/trends` implementados en el backend (agregar en este plan)

## Backend — Endpoints de Dashboard

### DashboardRoutes.kt

```kotlin
package com.receiptanalyzer.dashboard

import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

fun Route.dashboardRoutes(service: DashboardService) {
    route("/api/dashboard") {
        get("/summary") {
            val userId = UUID.fromString("...") // TODO: auth
            val from = Instant.now().minus(30, ChronoUnit.DAYS)
            val to = Instant.now()
            call.respond(service.getSummary(userId, from, to))
        }
        get("/trends") {
            val userId = UUID.fromString("...") // TODO: auth
            call.respond(service.getMonthlyTrends(userId))
        }
    }
}
```

### DashboardService.kt

```kotlin
package com.receiptanalyzer.dashboard

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.UUID

data class DashboardSummary(
    val totalThisMonth: Double,
    val receiptCount: Int,
    val averageReceipt: Double,
    val totalSavings: Double,      // descuentos aplicados este mes
    val byCategory: List<CategoryTotal>,
)

data class CategoryTotal(val category: String, val total: Double, val itemCount: Int)

data class MonthlyTrend(val month: String, val total: Double, val receiptCount: Int)

class DashboardService {

    suspend fun getSummary(userId: UUID, from: Instant, to: Instant): DashboardSummary =
        newSuspendedTransaction {
            // Usar la función SQL get_spending_by_category del Plan 01
            val categories = exec(
                "SELECT category, total_spent, item_count FROM get_spending_by_category(?, ?, ?)",
                args = listOf(userId, from, to)
            ) { rs ->
                buildList {
                    while (rs.next()) add(CategoryTotal(
                        category = rs.getString("category") ?: "Sin categoría",
                        total = rs.getDouble("total_spent"),
                        itemCount = rs.getInt("item_count"),
                    ))
                }
            } ?: emptyList()

            val receipts = com.receiptanalyzer.receipt.ReceiptsTable
                .select {
                    (com.receiptanalyzer.receipt.ReceiptsTable.userId eq userId) and
                    (com.receiptanalyzer.receipt.ReceiptsTable.receiptDate greaterEq from) and
                    (com.receiptanalyzer.receipt.ReceiptsTable.receiptDate lessEq to)
                }.toList()

            val totalThisMonth = receipts.sumOf { it[com.receiptanalyzer.receipt.ReceiptsTable.total].toDouble() }
            val totalSavings = receipts.sumOf { it[com.receiptanalyzer.receipt.ReceiptsTable.totalDiscount].toDouble() }

            DashboardSummary(
                totalThisMonth = totalThisMonth,
                receiptCount = receipts.size,
                averageReceipt = if (receipts.isEmpty()) 0.0 else totalThisMonth / receipts.size,
                totalSavings = totalSavings,
                byCategory = categories,
            )
        }

    suspend fun getMonthlyTrends(userId: UUID): List<MonthlyTrend> =
        newSuspendedTransaction {
            exec(
                """
                SELECT to_char(receipt_date, 'YYYY-MM') as month,
                       SUM(total) as total,
                       COUNT(*) as receipt_count
                FROM receipts
                WHERE user_id = ?
                  AND receipt_date > now() - interval '12 months'
                GROUP BY month
                ORDER BY month
                """,
                args = listOf(userId)
            ) { rs ->
                buildList {
                    while (rs.next()) add(MonthlyTrend(
                        month = rs.getString("month"),
                        total = rs.getDouble("total"),
                        receiptCount = rs.getInt("receipt_count"),
                    ))
                }
            } ?: emptyList()
        }
}
```

## Frontend

### 1. Instalar Chart.js

```bash
npm install chart.js
```

### 2. src/lib/components/SpendingChart.svelte

```svelte
<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { Chart, registerables } from 'chart.js';
  Chart.register(...registerables);

  export let trends: Array<{ month: string; total: number }> = [];

  let canvas: HTMLCanvasElement;
  let chart: Chart;

  onMount(() => {
    chart = new Chart(canvas, {
      type: 'line',
      data: {
        labels: trends.map(t => {
          const [year, month] = t.month.split('-');
          return new Date(+year, +month - 1).toLocaleDateString('es-AR', { month: 'short', year: '2-digit' });
        }),
        datasets: [{
          label: 'Gasto mensual',
          data: trends.map(t => t.total),
          borderColor: '#2563eb',
          backgroundColor: '#dbeafe',
          fill: true,
          tension: 0.3,
        }],
      },
      options: {
        responsive: true,
        plugins: { legend: { display: false } },
        scales: {
          y: {
            ticks: {
              callback: (v) => `$${(+v).toLocaleString('es-AR')}`,
            },
          },
        },
      },
    });
  });

  onDestroy(() => chart?.destroy());
</script>

<canvas bind:this={canvas}></canvas>
```

### 3. src/routes/+page.svelte (dashboard)

```svelte
<script lang="ts">
  import { onMount } from 'svelte';
  import SpendingChart from '$lib/components/SpendingChart.svelte';

  const BACKEND = import.meta.env.PUBLIC_BACKEND_URL;
  let summary: any = null;
  let trends: any[] = [];

  onMount(async () => {
    const { authHeaders } = await import('$lib/api');
    const headers = await authHeaders();
    [summary, trends] = await Promise.all([
      fetch(`${BACKEND}/api/dashboard/summary`, { headers }).then(r => r.json()),
      fetch(`${BACKEND}/api/dashboard/trends`, { headers }).then(r => r.json()),
    ]);
  });
</script>

<div class="space-y-6">
  <h1 class="text-xl font-semibold">Dashboard</h1>

  {#if summary}
    <!-- Métricas rápidas -->
    <div class="grid grid-cols-2 sm:grid-cols-4 gap-4">
      {#each [
        { label: 'Gasto este mes', value: `$${summary.totalThisMonth?.toLocaleString('es-AR')}` },
        { label: 'Tickets', value: summary.receiptCount },
        { label: 'Promedio por ticket', value: `$${summary.averageReceipt?.toLocaleString('es-AR')}` },
        { label: 'Ahorrado (descuentos)', value: `$${summary.totalSavings?.toLocaleString('es-AR')}`, green: true },
      ] as metric}
        <div class="bg-white rounded-xl border p-4">
          <p class="text-xs text-gray-400 mb-1">{metric.label}</p>
          <p class="text-xl font-bold" class:text-green-600={metric.green}>{metric.value}</p>
        </div>
      {/each}
    </div>

    <!-- Gráfico de tendencia -->
    {#if trends.length > 0}
      <div class="bg-white rounded-xl border p-5">
        <h2 class="text-sm font-semibold text-gray-600 mb-4">Gasto mensual (últimos 12 meses)</h2>
        <SpendingChart {trends} />
      </div>
    {/if}

    <!-- Gasto por categoría -->
    {#if summary.byCategory?.length > 0}
      <div class="bg-white rounded-xl border p-5">
        <h2 class="text-sm font-semibold text-gray-600 mb-4">Por categoría (este mes)</h2>
        <div class="space-y-2">
          {#each summary.byCategory as cat}
            {@const pct = summary.totalThisMonth > 0 ? (cat.total / summary.totalThisMonth * 100) : 0}
            <div>
              <div class="flex justify-between text-sm mb-1">
                <span>{cat.category}</span>
                <span class="font-medium">${cat.total?.toLocaleString('es-AR')}</span>
              </div>
              <div class="h-1.5 bg-gray-100 rounded-full">
                <div class="h-1.5 bg-blue-500 rounded-full" style="width: {pct}%"></div>
              </div>
            </div>
          {/each}
        </div>
      </div>
    {/if}
  {:else}
    <p class="text-gray-400">Cargando...</p>
  {/if}
</div>
```

## Verificación

1. Con tickets cargados, ir a `/` → ver las 4 métricas (gasto, cantidad, promedio, ahorro).
2. Ver el gráfico de línea con el historial mensual.
3. Ver las barras de porcentaje por categoría (Almacén, Fiambrería, etc.).

## Archivos que se crean/modifican
- `backend/src/main/kotlin/com/receiptanalyzer/dashboard/DashboardService.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/dashboard/DashboardRoutes.kt`
- `backend/src/main/kotlin/com/receiptanalyzer/config/Routing.kt` (agregar dashboardRoutes)
- `frontend/package.json` (agregar chart.js)
- `frontend/src/lib/components/SpendingChart.svelte`
- `frontend/src/routes/+page.svelte` (reemplazar redirect con dashboard)
