// Synthetic response-body baseline only. Never accepts a Hub address or secret.
import { readFile } from 'node:fs/promises';
const upstream = JSON.parse(await readFile(new URL('../upstream.json', import.meta.url), 'utf8'));
if (!/^v\d+\.\d+\.\d+$/.test(upstream.tag)) throw new Error('Invalid fixture version');
const endpoints = ['health', 'stats', 'history'];
const bytes = Object.fromEntries(await Promise.all(endpoints.map(async name => [name,
  (await readFile(new URL(`../app/src/test/resources/protocol/${upstream.tag}/${name}.json`, import.meta.url))).length])));
const perRun = Object.values(bytes).reduce((sum, size) => sum + size, 0);
console.log(JSON.stringify({ fixture: upstream.tag, responseBodyBytes: bytes, perRun,
  expectedBodyBytesPerDay: { thirtyMinutes: perRun * 48, sixtyMinutes: perRun * 24 },
  limitations: 'Synthetic payload only; excludes HTTP/TLS/VPN overhead and retries. Use phone traffic counters for overnight totals.' }, null, 2));
