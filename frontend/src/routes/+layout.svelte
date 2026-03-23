<script lang="ts">
	import '../app.css';
	import { supabase } from '$lib/supabase';
	import { goto } from '$app/navigation';
	import { page } from '$app/stores';
	import { onMount } from 'svelte';

	const PUBLIC_ROUTES = ['/login'];

	onMount(async () => {
		const {
			data: { session }
		} = await supabase.auth.getSession();
		if (!session && !PUBLIC_ROUTES.includes($page.url.pathname)) {
			goto('/login');
		}

		supabase.auth.onAuthStateChange((event) => {
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
