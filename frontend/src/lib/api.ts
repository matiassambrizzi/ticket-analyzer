import { PUBLIC_BACKEND_URL } from '$env/static/public';
import { supabase } from './supabase';
import type { Async } from './types';

const makeRequest = (path: string, options: RequestInit = {}): Async<unknown> => async () => {
	const { data } = await supabase.auth.getSession();
	const token = data.session?.access_token;
	const res = await fetch(`${PUBLIC_BACKEND_URL}${path}`, {
		...options,
		headers: {
			...(token ? { Authorization: `Bearer ${token}` } : {}),
			...options.headers
		}
	});
	return res.json();
};

export const uploadReceipt = (file: File): Async<unknown> => {
	const formData = new FormData();
	formData.append('file', file);
	return makeRequest('/api/receipts', { method: 'POST', body: formData });
};

export const getReceipts = (): Async<unknown> => makeRequest('/api/receipts');

export const getReceipt = (id: string): Async<unknown> => makeRequest(`/api/receipts/${id}`);

export const getReceiptStatus = (id: string): Async<unknown> =>
	makeRequest(`/api/receipts/${id}/status`);
