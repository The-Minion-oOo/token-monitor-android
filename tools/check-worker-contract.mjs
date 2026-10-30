import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

import { usageProjection } from "./check-v082-usage.mjs";

/** Called only inside the disposable, pinned-source contract process. No sockets or deployment. */
export async function checkWorkerContract(source, output, upstream, limitCases = {}) {
    const { HubDO } = await import(pathToFileURL(join(source, "worker/src/index.js")));
    const records = new Map();
    const storage = {
        async get(key) { return records.get(key); },
        async put(key, value) { records.set(key, structuredClone(value)); },
        async delete(key) { records.delete(key); },
        async list({ prefix } = {}) { return new Map([...records].filter(([key]) => !prefix || key.startsWith(prefix))); },
    };
    const secret = "synthetic-worker-contract-secret";
    const hub = new HubDO({ storage }, { TOKEN_MONITOR_SECRET: secret, STALE_AFTER_MS: "600000" });
    // HubDO.fetch handles these Request objects in memory. global fetch is never called.
    const request = (path, authenticated = true, init = {}) => hub.fetch(new Request(`https://hub.example.com/api/${path}`, {
        ...init, headers: { ...(authenticated ? { Authorization: `Bearer ${secret}` } : {}), ...init.headers },
    }));
    const now = Date.now();
    const at = offset => new Date(now + offset).toISOString();
    const sample = {
        deviceId: "synthetic-worker-desktop", hostname: "Synthetic desktop", agentVersion: upstream.version,
        updatedAt: at(0), today: { totalTokens: 1234, costUsd: 0.42, clients: { mcode: 1234 },
            sessions: { sample: { totalTokens: 1234, lastUsedAt: at(0), title: "NEVER_EXPORT_TITLE",
                prompt: "NEVER_EXPORT_PROMPT", messages: ["NEVER_EXPORT_MESSAGES"] } } },
    };
    const ingest = async payload => {
        const response = await request("ingest", true, { method: "POST", headers: { "content-type": "application/json", "x-token-monitor-response": "minimal" }, body: JSON.stringify(payload) });
        assert.equal(response.status, 200);
        await response.text();
    };
    const abort = new AbortController();
    let reader, pump;
    const frames = [];
    const waitForFrame = async () => {
        const deadline = Date.now() + 5000;
        while (!frames.length) {
            assert.ok(Date.now() < deadline, "Timed out waiting for Worker stream frame");
            await new Promise(resolve => setTimeout(resolve, 10));
        }
        return frames.shift();
    };
    const responses = {};
    try {
        await ingest(sample);
        for (const name of ["health", "stats", "devices", "history", "subscriptions"]) {
            if (name !== "health") {
                assert.equal((await request(name, false)).status, 401, `${name} must require auth`);
                assert.equal((await hub.fetch(new Request(`https://hub.example.com/api/${name}`, { headers: { Authorization: "Bearer wrong-synthetic-secret" } }))).status, 401);
            }
            const response = await request(name, name !== "health");
            assert.equal(response.status, 200, `${name} read`);
            responses[`${name}.json`] = await response.json();
        }
        assert.equal(responses["health.json"].role, "hub");
        assert.equal(responses["stats.json"].periods.today.totalTokens, 1234);
        assert.ok(!JSON.stringify(responses).includes("NEVER_EXPORT"), "Private conversation content crossed Worker boundary");
        assert.equal((await request("stats/stream", false)).status, 401);
        const response = await request("stats/stream", true, { signal: abort.signal, headers: { "x-token-monitor-stream": "3" } });
        assert.equal(response.status, 200);
        reader = response.body.getReader();
        pump = (async () => {
            const decoder = new TextDecoder();
            let buffer = "";
            while (true) {
                const { value, done } = await reader.read();
                if (done) return;
                buffer += decoder.decode(value, { stream: true });
                let boundary;
                while ((boundary = buffer.indexOf("\n\n")) !== -1) {
                    const block = buffer.slice(0, boundary);
                    buffer = buffer.slice(boundary + 2);
                    const event = /^event:\s*(.+)$/m.exec(block)?.[1];
                    const data = /^data:\s*(.+)$/m.exec(block)?.[1];
                    if (event && data) frames.push({ event, data: JSON.parse(data) });
                }
            }
        })();
        const snapshot = await waitForFrame();
        assert.equal(snapshot.event, "snapshot");
        responses["stream-snapshot.json"] = snapshot.data;
        await ingest({ ...sample, updatedAt: at(1000) });
        const freshness = await waitForFrame();
        assert.equal(freshness.event, "freshness");
        assert.equal(freshness.data.stats.devices[0].updatedAt, at(1000));
        responses["stream-freshness.json"] = freshness.data;
        await ingest({ ...sample, updatedAt: at(2000), today: { ...sample.today, totalTokens: 2468 } });
        const changed = await waitForFrame();
        assert.equal(changed.event, "stats");
        assert.equal(changed.data.stats.periods.today.totalTokens, 2468);
        responses["stream-stats.json"] = changed.data;
        await hub.broadcast("subscriptions");
        const forced = await waitForFrame();
        assert.equal(forced.event, "stats", "Non-ingest changes require complete stats");
        for (const [name, fixture] of Object.entries(limitCases)) {
            const caseRecords = new Map();
            const caseHub = new HubDO({ storage: {
                async get(key) { return caseRecords.get(key); },
                async put(key, value) { caseRecords.set(key, structuredClone(value)); },
                async delete(key) { caseRecords.delete(key); },
                async list({ prefix } = {}) { return new Map([...caseRecords].filter(([key]) => !prefix || key.startsWith(prefix))); },
            } }, { TOKEN_MONITOR_SECRET: secret, STALE_AFTER_MS: "600000" });
            try {
                for (const record of fixture.records) {
                    const result = await caseHub.fetch(new Request("https://hub.example.com/api/ingest", {
                        method: "POST", headers: { Authorization: `Bearer ${secret}`, "content-type": "application/json" },
                        body: JSON.stringify(record),
                    }));
                    assert.equal(result.status, 200);
                    await result.text();
                }
                const result = await caseHub.fetch(new Request("https://hub.example.com/api/stats", {
                    headers: { Authorization: `Bearer ${secret}` },
                }));
                assert.equal(result.status, 200);
                const stats = await result.json();
                const comparable = providers => providers.map(({ provider, accountKey, sourceDeviceId, planLabel, windows, status }) =>
                    ({ provider, accountKey, sourceDeviceId, planLabel, windows, status }));
                assert.deepEqual(comparable(stats.limits.providers), comparable(fixture.providers));
                if (fixture.usage) assert.deepEqual(usageProjection(stats.periods), fixture.usage);
                responses[`${name}.json`] = stats;
            } finally {
                if (caseHub.broadcastTimer) clearTimeout(caseHub.broadcastTimer);
                if (caseHub.heartbeatTimer) clearInterval(caseHub.heartbeatTimer);
            }
        }
        await mkdir(output, { recursive: true });
        for (const [name, value] of Object.entries(responses)) {
            assert.ok(!JSON.stringify(value).includes("NEVER_EXPORT"));
            await writeFile(join(output, name), `${JSON.stringify(value, null, 2)}\n`);
        }
        console.log(`Actual ${upstream.tag} Worker passed Bearer reads, snapshot, freshness and changed stats locally.`);
    } finally {
        abort.abort();
        for (const client of [...hub.sseClients]) hub.dropClient(client);
        await reader?.cancel().catch(() => {});
        await pump?.catch(() => {});
        if (hub.broadcastTimer) clearTimeout(hub.broadcastTimer);
        if (hub.heartbeatTimer) clearInterval(hub.heartbeatTimer);
    }
}
