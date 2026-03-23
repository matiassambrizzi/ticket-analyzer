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
			<input
				type="email"
				bind:value={email}
				placeholder="Email"
				class="w-full border rounded-lg px-3 py-2 text-sm"
				required
			/>
			<input
				type="password"
				bind:value={password}
				placeholder="Contraseña"
				class="w-full border rounded-lg px-3 py-2 text-sm"
				required
			/>
			{#if error}
				<p class="text-red-600 text-sm">{error}</p>
			{/if}
			<button
				type="submit"
				disabled={loading}
				class="w-full bg-blue-600 text-white py-2 rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
			>
				{loading ? 'Cargando...' : isSignup ? 'Registrarse' : 'Entrar'}
			</button>
		</form>
		<button
			on:click={() => (isSignup = !isSignup)}
			class="mt-4 text-sm text-center w-full text-gray-500 hover:text-gray-700"
		>
			{isSignup ? '¿Ya tenés cuenta? Iniciá sesión' : '¿No tenés cuenta? Registrate'}
		</button>
	</div>
</div>
