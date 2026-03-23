import { PUBLIC_BACKEND_URL } from '$env/static/public';
import { supabase } from './supabase';

const makeRequest = async (path: string, options: RequestInit = {}) => {
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

export const uploadReceipt = async (file: File) => {
	const formData = new FormData();
	formData.append('file', file);
	return makeRequest('/api/receipts', { method: 'POST', body: formData });
};

export const getReceipts = async () => makeRequest('/api/receipts');

export const getReceipt = async (id: string) => makeRequest(`/api/receipts/${id}`);

export const getReceiptStatus = async (id: string) => makeRequest(`/api/receipts/${id}/status`);
