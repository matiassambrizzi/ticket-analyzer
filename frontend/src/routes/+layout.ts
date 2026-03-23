import { supabase } from '$lib/supabase';
import type { LayoutLoad } from './$types';

export const load: LayoutLoad = async () => {
	const {
		data: { session }
	} = await supabase.auth.getSession();
	return { session };
};
