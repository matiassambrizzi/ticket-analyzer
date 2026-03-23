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
		body: formData
	});
	return res.json();
}

export async function getReceipts() {
	const res = await fetch(`${PUBLIC_BACKEND_URL}/api/receipts`, {
		headers: await authHeaders()
	});
	return res.json();
}

export async function getReceipt(id: string) {
	const res = await fetch(`${PUBLIC_BACKEND_URL}/api/receipts/${id}`, {
		headers: await authHeaders()
	});
	return res.json();
}

export async function getReceiptStatus(id: string) {
	const res = await fetch(`${PUBLIC_BACKEND_URL}/api/receipts/${id}/status`, {
		headers: await authHeaders()
	});
	return res.json();
}
