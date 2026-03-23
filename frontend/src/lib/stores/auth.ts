import { writable } from 'svelte/store';
import type { Session } from '@supabase/supabase-js';
import { supabase } from '$lib/supabase';

export const session = writable<Session | null>(null);

// Initialize session from Supabase and subscribe to auth changes
supabase.auth.getSession().then(({ data }) => {
	session.set(data.session);
});

supabase.auth.onAuthStateChange((_event, newSession) => {
	session.set(newSession);
});
