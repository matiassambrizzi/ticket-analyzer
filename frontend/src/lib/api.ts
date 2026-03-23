import { PUBLIC_BACKEND_URL } from '$env/static/public';
import { supabase } from './supabase';

async function client(path: string, options: RequestInit = {}) {
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
}

export async function uploadReceipt(file: File) {
	const formData = new FormData();
	formData.append('file', file);
	return client('/api/receipts', { method: 'POST', body: formData });
}

export async function getReceipts() {
	return client('/api/receipts');
}

export async function getReceipt(id: string) {
	return client(`/api/receipts/${id}`);
}

export async function getReceiptStatus(id: string) {
	return client(`/api/receipts/${id}/status`);
}
