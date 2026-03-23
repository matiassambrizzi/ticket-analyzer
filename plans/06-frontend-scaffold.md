# Plan 06 — Frontend: Scaffolding SvelteKit + Auth

## Objetivo
Crear el proyecto SvelteKit con Tailwind, configurar Supabase Auth, y tener login/logout funcionando con rutas protegidas.

## Pre-requisitos
- Plan 01 completado (Supabase configurado)
- Node.js 20+ instalado
- URL y anon key de Supabase del Plan 01

## Estructura a crear

```
frontend/
├── package.json
├── svelte.config.js
├── vite.config.ts
├── tailwind.config.ts
├── postcss.config.js
├── .env.example
├── src/
│   ├── app.html
│   ├── app.css
│   ├── lib/
│   │   ├── supabase.ts           # cliente Supabase
│   │   ├── api.ts                # cliente del backend Ktor
│   │   └── stores/
│   │       └── auth.ts           # Svelte store de sesión
│   └── routes/
│       ├── +layout.svelte        # layout global (navbar)
│       ├── +layout.server.ts     # guard de auth
│       ├── +layout.ts            # carga sesión
│       ├── +page.svelte          # home → redirige a /receipts
│       ├── login/
│       │   └── +page.svelte
│       └── receipts/
│           └── +page.svelte      # placeholder para Plan 07
└── static/
    └── favicon.png
```

## Pasos

### 1. Crear proyecto SvelteKit

```bash
cd receipt-analyzer
npm create svelte@latest frontend
# Opciones: Skeleton project, TypeScript, ESLint, Prettier
cd frontend
npm install
```

### 2. Instalar dependencias

```bash
npm install @supabase/supabase-js
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p
```

### 3. .env.example

```bash
PUBLIC_SUPABASE_URL=https://xxxxx.supabase.co
PUBLIC_SUPABASE_ANON_KEY=eyJ...
PUBLIC_BACKEND_URL=http://localhost:8080
```

### 4. tailwind.config.ts

```typescript
import type { Config } from 'tailwindcss';

export default {
  content: ['./src/**/*.{html,js,svelte,ts}'],
  theme: {
    extend: {},
  },
  plugins: [],
} satisfies Config;
```

### 5. src/app.css

```css
@tailwind base;
@tailwind components;
@tailwind utilities;
```

### 6. src/lib/supabase.ts

```typescript
import { createClient } from '@supabase/supabase-js';
import { PUBLIC_SUPABASE_URL, PUBLIC_SUPABASE_ANON_KEY } from '$env/static/public';

export const supabase = createClient(PUBLIC_SUPABASE_URL, PUBLIC_SUPABASE_ANON_KEY);
```

### 7. src/lib/api.ts

```typescript
import { PUBLIC_BACKEND_URL } from '$env/static/public';
import { supabase } from './supabase';

async function authHeaders(): Promise<HeadersInit> {
  const { data } = await supabase.auth.getSession();
  const token = data.session?.access_token;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function uploadReceipt(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${PUBLIC_BACKEND_URL}/api/receipts`, {
    method: 'POST',
    headers: await authHeaders(),
    body: formData,
  });
  return res.json();
}

export async function getReceipts() {
  const res = await fetch(`${PUBLIC_BACKEND_URL}/api/receipts`, {
    headers: await authHeaders(),
  });
  return res.json();
}

export async function getReceipt(id: string) {
  const res = await fetch(`${PUBLIC_BACKEND_URL}/api/receipts/${id}`, {
    headers: await authHeaders(),
  });
  return res.json();
}

export async function getReceiptStatus(id: string) {
  const res = await fetch(`${PUBLIC_BACKEND_URL}/api/receipts/${id}/status`, {
    headers: await authHeaders(),
  });
  return res.json();
}
```

### 8. src/routes/+layout.ts

```typescript
import { supabase } from '$lib/supabase';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = async () => {
  const { data: { session } } = await supabase.auth.getSession();
  return { session };
};
```

### 9. src/routes/+layout.server.ts

```typescript
import { redirect } from '@sveltejs/kit';
import type { LayoutServerLoad } from './$types';

const PUBLIC_ROUTES = ['/login'];

export const load: LayoutServerLoad = async ({ url, locals }) => {
  // En SvelteKit con Supabase, el auth se maneja client-side
  // Esta guarda se implementa en +layout.svelte
  return {};
};
```

### 10. src/routes/+layout.svelte

```svelte
<script lang="ts">
  import '../app.css';
  import { supabase } from '$lib/supabase';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import { onMount } from 'svelte';

  const PUBLIC_ROUTES = ['/login'];

  onMount(async () => {
    const { data: { session } } = await supabase.auth.getSession();
    if (!session && !PUBLIC_ROUTES.includes($page.url.pathname)) {
      goto('/login');
    }

    supabase.auth.onAuthStateChange((event, session) => {
      if (event === 'SIGNED_OUT') goto('/login');
    });
  });

  async function logout() {
    await supabase.auth.signOut();
    goto('/login');
  }
</script>

<div class="min-h-screen bg-gray-50">
  {#if !PUBLIC_ROUTES.includes($page.url.pathname)}
    <nav class="bg-white shadow-sm border-b">
      <div class="max-w-5xl mx-auto px-4 py-3 flex justify-between items-center">
        <a href="/receipts" class="font-semibold text-gray-900">Ticket Analyzer</a>
        <div class="flex gap-4 items-center text-sm">
          <a href="/receipts" class="text-gray-600 hover:text-gray-900">Mis tickets</a>
          <a href="/compare" class="text-gray-600 hover:text-gray-900">Comparar</a>
          <button on:click={logout} class="text-gray-500 hover:text-red-600">Salir</button>
        </div>
      </div>
    </nav>
  {/if}
  <main class="max-w-5xl mx-auto px-4 py-8">
    <slot />
  </main>
</div>
```

### 11. src/routes/login/+page.svelte

```svelte
<script lang="ts">
  import { supabase } from '$lib/supabase';
  import { goto } from '$app/navigation';

  let email = '';
  let password = '';
  let error = '';
  let loading = false;
  let isSignup = false;

  async function submit() {
    loading = true;
    error = '';
    const fn = isSignup
      ? supabase.auth.signUp({ email, password })
      : supabase.auth.signInWithPassword({ email, password });
    const { error: err } = await fn;
    if (err) {
      error = err.message;
    } else {
      goto('/receipts');
    }
    loading = false;
  }
</script>

<div class="max-w-sm mx-auto mt-20">
  <h1 class="text-2xl font-bold text-center mb-8">Ticket Analyzer</h1>
  <div class="bg-white rounded-xl shadow p-8">
    <h2 class="text-lg font-semibold mb-6">{isSignup ? 'Crear cuenta' : 'Iniciar sesión'}</h2>
    <form on:submit|preventDefault={submit} class="space-y-4">
      <input type="email" bind:value={email} placeholder="Email"
        class="w-full border rounded-lg px-3 py-2 text-sm" required />
      <input type="password" bind:value={password} placeholder="Contraseña"
        class="w-full border rounded-lg px-3 py-2 text-sm" required />
      {#if error}
        <p class="text-red-600 text-sm">{error}</p>
      {/if}
      <button type="submit" disabled={loading}
        class="w-full bg-blue-600 text-white py-2 rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
        {loading ? 'Cargando...' : isSignup ? 'Registrarse' : 'Entrar'}
      </button>
    </form>
    <button on:click={() => isSignup = !isSignup}
      class="mt-4 text-sm text-center w-full text-gray-500 hover:text-gray-700">
      {isSignup ? '¿Ya tenés cuenta? Iniciá sesión' : '¿No tenés cuenta? Registrate'}
    </button>
  </div>
</div>
```

### 12. src/routes/+page.svelte (redirect)

```svelte
<script>
  import { goto } from '$app/navigation';
  import { onMount } from 'svelte';
  onMount(() => goto('/receipts'));
</script>
```

### 13. src/routes/receipts/+page.svelte (placeholder)

```svelte
<h1 class="text-xl font-semibold">Mis tickets</h1>
<p class="text-gray-500 mt-2">Próximamente: lista de tickets y upload (Plan 07)</p>
```

## Verificación

1. `npm run dev` → `http://localhost:5173`
2. Acceder a `/receipts` sin estar logueado → redirige a `/login`
3. Crear cuenta con email/password
4. Después del login → redirige a `/receipts`
5. Click "Salir" → cierra sesión y vuelve a `/login`

## Archivos que se crean
- `frontend/` (todo el directorio)
- Todos los archivos listados en la estructura arriba
