'use client';
import { useEffect, useState } from 'react';
const SSO_URL = process.env.NEXT_PUBLIC_SSO_URL || 'https://abc.sso.com/';
const API_BASE = process.env.NEXT_PUBLIC_API_BASE || '/backend';

export default function Home() {
  const [user, setUser] = useState(null);
  const [data, setData] = useState(null);
  const [value, setValue] = useState('');
  const [message, setMessage] = useState('');

  async function api(path, options = {}) {
    const headers = { ...options.headers };
    if (options.body) headers['Content-Type'] = 'application/json';
    if (options.method && options.method !== 'GET' && user) {
      headers[user.csrfHeader] = user.csrfToken;
    }
    const response = await fetch(`${API_BASE}${path}`, {
      ...options, headers, credentials: 'include', cache: 'no-store',
    });
    if (response.status === 401) {
      window.location.assign(SSO_URL);
      return null;
    }
    if (response.status === 403) throw new Error('Access denied. You do not have permission, or your security token is invalid.');
    if (!response.ok) throw new Error(`Request failed (${response.status})`);
    return response;
  }

  useEffect(() => {
    api('/api/auth/me').then(async response => {
      if (response) setUser(await response.json());
    }).catch(error => setMessage(error.message));
  }, []);

  async function load() {
    try {
      const response = await api('/api/data');
      if (response) setData(await response.json());
    } catch (error) { setMessage(error.message); }
  }
  async function save(event) {
    event.preventDefault();
    try {
      const response = await api('/api/data', { method: 'POST', body: JSON.stringify({ value }) });
      if (response) { setMessage('Saved'); setValue(''); await load(); }
    } catch (error) { setMessage(error.message); }
  }
  async function logout() {
    try {
      const response = await api('/api/auth/logout', { method: 'POST' });
      if (response) window.location.assign(SSO_URL);
    } catch (error) { setMessage(error.message); }
  }

  return <main style={{ padding: 40, fontFamily: 'sans-serif', maxWidth: 900 }}>
    <h1>My App</h1>
    {message && <p role="alert">{message}</p>}
    {!user && !message && <p>Checking session...</p>}
    {user && <>
      <p>Signed in as <b>{user.fullName}</b> ({user.userId})</p>
      <p>Permissions: {user.permissions.join(', ')}</p>
      <button onClick={load}>GET data</button>
      <pre>{data ? JSON.stringify(data, null, 2) : 'No data loaded'}</pre>
      {user.permissions.includes('USER') && <form onSubmit={save}>
        <label>Value <input value={value} onChange={event => setValue(event.target.value)} /></label>
        <button>POST data</button>
      </form>}
      <button onClick={logout}>Logout</button>
    </>}
  </main>;
}
