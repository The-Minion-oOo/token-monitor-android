import assert from "node:assert/strict";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

export async function checkV093Qoder(require, { createHub, source, secret, now, device }) {
    const { fetchQoderLimits } = require("./src/shared/providers/qoder/limits.js");
    const { normalizeClientName } = require("./src/shared/usage.js");
    const readQuota = (site, cookie) => fetchQoderLimits({ qoderSite: site, qoderCookie: cookie }, {
        env: {}, now: () => Date.parse(now), fetch: async url => {
            switch (new URL(url).pathname) {
                case "/api/v2/me/usages/big_model_credits":
                    return Response.json({ data: { user_id: "NEVER_EXPORT_USER", total_quota: {
                        quota_summary: { used_value: 25, limit_value: 100, remaining_value: 75, unit: "credits" },
                    } } });
                case "/api/v1/me/organization-shared-usages/big_model_credits":
                    return new Response(null, { status: 404 });
                case "/api/v1/me/userplan":
                    return Response.json({ plan_tier: "PLAN_TIER_TEAM" });
                default:
                    throw new Error(`Unexpected quota endpoint: ${url}`);
            }
        },
    });
    const [before, after, cn] = await Promise.all([
        readQuota("global", "session=NEVER_EXPORT_A"),
        readQuota("global", "session=NEVER_EXPORT_B"),
        readQuota("cn", "session=NEVER_EXPORT_B"),
    ]);
    assert.equal(before.status, "ok");
    assert.equal(after.accountKey, before.accountKey);
    assert.notEqual(after.accountKeyAliases[0], before.accountKeyAliases[0]);
    assert.notEqual(after.accountKey, cn.accountKey);
    assert.equal(normalizeClientName("qoder"), "qoder");
    assert.equal(normalizeClientName("qoder-cn"), "qodercn");

    const sessions = {
        "qoder:demo-global": { client: "qoder", sessionId: "demo-global", totalTokens: 90, models: { "demo-model": 90 } },
        "qodercn:demo-cn": { client: "qodercn", sessionId: "demo-cn", totalTokens: 40, models: { "demo-model": 40 } },
    };
    const period = { totalTokens: 130, clients: { qoder: 90, qodercn: 40 }, sessions };
    const record = { ...device, deviceId: "synthetic-qoder-editions", trackedClients: ["qoder", "qodercn"],
        periods: { today: period, month: period, allTime: period }, history: {}, limits: { providers: [after] } };
    const hub = createHub({ host: "127.0.0.1", port: 0, secret, dataFile: join(source, "v093-qoder-store.json") });
    hub.ingest(record);
    await hub.start();
    try {
        const response = await fetch(`http://127.0.0.1:${hub.server.address().port}/api/stats`, {
            headers: { Authorization: `Bearer ${secret}` }, signal: AbortSignal.timeout(5000), redirect: "error",
        });
        assert.equal(response.status, 200);
        const stats = await response.json();
        assert.deepEqual(stats.periods.today.clients, period.clients);
        assert.equal(stats.limits.providers[0].accountKey, after.accountKey);
        assert.deepEqual(stats.limits.providers[0].accountKeyAliases, after.accountKeyAliases);
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        return { records: [record], stats };
    } finally {
        await hub.stop();
    }
}

export async function checkV093WorkerReplacement(source) {
    const { HubDO } = await import(pathToFileURL(join(source, "worker/src/index.js")));
    const records = new Map();
    let current = true;
    const storage = {
        async get(key) {
            if (key === "sse-liveness" && !current) throw new Error("Synthetic Worker replacement");
            return records.get(key);
        },
        async put(key, value) { records.set(key, structuredClone(value)); },
        async delete(key) { records.delete(key); },
        async list({ prefix = "" } = {}) { return new Map([...records].filter(([key]) => key.startsWith(prefix))); },
    };
    const secret = "synthetic-replacement-secret";
    const environment = { TOKEN_MONITOR_SECRET: secret };
    const open = (hub, signal) => hub.fetch(new Request("https://hub.example.com/api/stats/stream", {
        signal, headers: { Authorization: `Bearer ${secret}`, "x-token-monitor-stream": "4" },
    }));
    const original = new HubDO({ storage }, environment);
    const abort = new AbortController();
    const interval = globalThis.setInterval;
    let heartbeat;
    let reader;
    try {
        globalThis.setInterval = (callback, delay, ...args) => {
            if (delay === 30_000) heartbeat = callback;
            return interval(callback, delay, ...args);
        };
        const response = await open(original, abort.signal);
        assert.equal(response.status, 200);
        reader = response.body.getReader();
        const decoder = new TextDecoder();
        assert.match(decoder.decode((await reader.read()).value), /^event: snapshot/);
        await new Promise(setImmediate);
        assert.equal(typeof heartbeat, "function");
        const healthy = reader.read();
        await heartbeat();
        assert.equal(decoder.decode((await healthy).value), ": hb\n\n");
        current = false;
        const closed = reader.read();
        const rejection = assert.rejects(closed, /Synthetic Worker replacement/);
        await heartbeat();
        await rejection;
        assert.equal(original.sseClients.size, 0);
    } finally {
        globalThis.setInterval = interval;
        abort.abort();
        await reader?.cancel().catch(() => {});
        if (original.heartbeatTimer) clearInterval(original.heartbeatTimer);
    }
    const replacement = new HubDO({ storage }, environment);
    const nextAbort = new AbortController();
    let nextReader;
    try {
        const ingest = await replacement.fetch(new Request("https://hub.example.com/api/ingest", {
            method: "POST", headers: { Authorization: `Bearer ${secret}`, "content-type": "application/json" },
            body: JSON.stringify({ deviceId: "synthetic-replacement", today: { totalTokens: 93 } }),
        }));
        assert.equal(ingest.status, 200);
        await ingest.text();
        const response = await open(replacement, nextAbort.signal);
        assert.equal(response.status, 200);
        nextReader = response.body.getReader();
        const frame = new TextDecoder().decode((await nextReader.read()).value);
        assert.match(frame, /^event: snapshot/);
        const snapshot = JSON.parse(/^data: (.+)$/m.exec(frame)[1]);
        assert.equal(snapshot.stats.periods.today.totalTokens, 93);
        return snapshot;
    } finally {
        nextAbort.abort();
        await nextReader?.cancel().catch(() => {});
        if (replacement.heartbeatTimer) clearInterval(replacement.heartbeatTimer);
        if (replacement.broadcastTimer) clearTimeout(replacement.broadcastTimer);
    }
}
