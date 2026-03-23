import type { LayoutServerLoad } from './$types';

// Auth guard is handled client-side in +layout.svelte
// because Supabase session is managed on the client
export const load: LayoutServerLoad = async () => {
	return {};
};
