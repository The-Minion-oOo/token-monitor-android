import assert from "node:assert/strict";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { mkdir, writeFile } from "node:fs/promises";
import { createHash } from "node:crypto";

const canonical = value => Array.isArray(value) ? value.map(canonical) : value && typeof value === "object"
    ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;

export async function checkV081Limits(require, context) {
    const { fetchArkcliLimits } = require("./src/shared/providers/volcengine/arkcli.js");
    const { mapCodexRateLimitsToProvider } = require("./src/shared/providers/codex/limits.js");
    assert.deepEqual(["prolite", "pro", "promax"].map(planType => mapCodexRateLimitsToProvider({ account: { planType },
        rateLimits: { primary: { usedPercent: 10, windowDurationMins: 300 } } }, { updatedAt: context.now }).accountLabel), ["Pro 100", "Pro 200", "Pro 500"]);
    const cases = {}, responses = {};
    for (const variant of ["personal", "missing", "unavailable"]) {
        const rows = await fetchArkcliLimits({ env: {}, runArkcli: async args => {
            if (args[0] === "auth") return { logged_in: true, auth_method: "sso", active_profile: { name: "synthetic" } };
            assert.ok(args.includes("synthetic"));
            if (args[0] === "plans") return { plans: ["agent-plan", "coding-plan"].map(key => ({ key, scope: "personal", status: "Running" })) };
            const product = args[args.indexOf("--product") + 1];
            if (product === "coding-plan" && variant === "unavailable") throw new Error("NEVER_EXPORT_FAILURE");
            const periods = product === "coding-plan"
                ? [{ label: "session", percent: 0 }, { label: "weekly", percent: 12.5 }, { label: "monthly", percent: 100 }]
                : [{ label: "5h", total: 100, percent: 0 }, { label: "weekly", total: 100, used: 25 }];
            if (product === "coding-plan" && variant === "missing") periods.splice(1);
            return { viewer: { account_id: "NEVER_EXPORT_ACCOUNT", user_id: "NEVER_EXPORT_USER", region: "cn-beijing" },
                items: [{ product, subscribed: true, tier: "Synthetic personal", periods }] };
        } }, context.now);
        const record = { ...context.device, deviceId: "v081-synthetic-desktop", limits: { providers: rows, updatedAt: context.now } };
        const hub = context.createHub({ secret: context.secret, dataFile: join(context.source, `synthetic-volc-${variant}.json`) });
        hub.ingest(record);
        const stats = hub.getStats();
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        assert.equal(stats.limits.providers.length, 2);
        const coding = stats.limits.providers.find(row => row.accountLabel === "Coding Plan");
        if (variant === "personal") assert.deepEqual(coding.windows.map(w => w.remainingPercent), [100, 87.5, 0]);
        if (variant === "missing") assert.equal(coding.windows.length, 1);
        if (variant === "unavailable") assert.ok(stats.limits.providers.some(row => row.status === "unavailable"));
        cases[`volc-${variant}`] = { records: [record], providers: stats.limits.providers };
        responses[`volc-${variant}.json`] = stats;
    }
    return { cases, responses };
}

/** Actual released Node and Worker streams, using only isolated synthetic records. */
export async function checkV081Streams(require, source, output, upstream) {
    const { createHub } = require("./src/hub/server.js");
    const { createStatsStreamReceiver } = require("./src/shared/hubProtocol.js");
    const { HubDO } = await import(pathToFileURL(join(source, "worker/src/index.js")));
    for (const kind of ["node", "worker"]) {
        const secret = "synthetic-stream-secret";
        const storage = new Map();
        const hub = kind === "node"
            ? createHub({ host: "127.0.0.1", port: 0, secret, broadcastDelayMs: 5, dataFile: join(source, "synthetic-v4.json") })
            : new HubDO({ storage: {
                async get(key) { return storage.get(key); }, async put(key, value) { storage.set(key, structuredClone(value)); },
                async delete(key) { storage.delete(key); }, async list() { return new Map(storage); },
            } }, { TOKEN_MONITOR_SECRET: secret, STALE_AFTER_MS: "600000" });
        if (kind === "node") await hub.start();
        const base = kind === "node" ? `http://127.0.0.1:${hub.server.address().port}` : "https://hub.example.com";
        const request = async (path, init = {}) => {
            const req = new Request(`${base}/api/${path}`, { ...init, headers: { Authorization: `Bearer ${secret}`, ...init.headers } });
            return kind === "node" ? fetch(req) : hub.fetch(req);
        };
        const now = Date.now() - 10_000, at = offset => new Date(now + offset).toISOString();
        const sessions = Object.fromEntries(Array.from({ length: 8 }, (_, i) => [`codex:sample/${i}~key`, {
            sessionId: `sample/${i}~key`, client: "codex", totalTokens: 100, models: { "gpt-5": 100 },
            lastUsedAt: at(0), reasoningEffort: "high", liveActivity: { state: "running", observedAt: at(0) },
            prompt: "NEVER_EXPORT_PROMPT", messages: ["NEVER_EXPORT_MESSAGE"],
        }]));
        let device = { deviceId: "synthetic-v4-desktop", hostname: "Synthetic desktop", agentVersion: upstream.version,
            updatedAt: at(0), clientHealth: { observedAt: at(0), sources: {} },
            periods: { today: { totalTokens: 3000, clients: { codex: 3000 }, sessions } } };
        const ingest = async () => {
            if (kind === "node") hub.ingest(device);
            else { const r = await request("ingest", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(device) }); assert.equal(r.status, 200); await r.text(); }
        };
        const abort = new AbortController();
        let reader, pump;
        const frames = [], records = [];
        try {
            await ingest();
            const response = await request("stats/stream", { signal: abort.signal, headers: { "x-token-monitor-stream": "4" } });
            assert.equal(response.status, 200);
            reader = response.body.getReader();
            pump = (async () => {
                let buffer = ""; const decoder = new TextDecoder();
                while (true) {
                    const { value, done } = await reader.read(); if (done) return;
                    buffer += decoder.decode(value, { stream: true });
                    let boundary;
                    while ((boundary = buffer.indexOf("\n\n")) >= 0) {
                        const block = buffer.slice(0, boundary); buffer = buffer.slice(boundary + 2);
                        const event = /^event:\s*(.+)$/m.exec(block)?.[1], data = /^data:\s*(.+)$/m.exec(block)?.[1];
                        if (event && data) frames.push({ event, data: JSON.parse(data), bytes: Buffer.byteLength(block) });
                    }
                }
            })();
            const receiver = createStatsStreamReceiver();
            const next = async () => {
                const end = Date.now() + 5000;
                while (!frames.length) { assert.ok(Date.now() < end, `${kind} v4 stream timed out`); await new Promise(r => setTimeout(r, 10)); }
                const frame = frames.shift();
                const stats = receiver.receive(frame.event, frame.data);
                assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
                records.push({ ...frame, expected: structuredClone(stats) });
                return { frame, stats };
            };
            assert.equal((await next()).frame.event, "snapshot");
            device = { ...device, updatedAt: at(1000) }; await ingest(); await next(); // bootstrap if the initial snapshot is unrevisioned
            device = { ...device, updatedAt: at(2000), clientHealth: { ...device.clientHealth, observedAt: at(2000) },
                periods: { today: { ...device.periods.today, sessions: Object.fromEntries(Object.entries(sessions).map(([key, row]) =>
                    [key, { ...row, liveActivity: { ...row.liveActivity, observedAt: at(2000) } }])) } } };
            await ingest();
            const fresh = await next();
            assert.equal(fresh.frame.event, "freshness");
            assert.equal(Object.values(fresh.stats.periods.today.sessions)[0].liveActivity.observedAt, at(2000));
            for (const effort of ["xhigh", null]) {
                const key = Object.keys(device.periods.today.sessions)[0];
                const rows = { ...device.periods.today.sessions, [key]: { ...device.periods.today.sessions[key], reasoningEffort: effort } };
                device = { ...device, updatedAt: at(3000), periods: { today: { ...device.periods.today, sessions: rows } } };
                await ingest(); const changed = await next(); assert.equal(changed.frame.event, "delta");
                assert.equal(Object.values(changed.stats.periods.today.sessions).find(row => row.sessionId === sessions[key].sessionId).reasoningEffort, effort);
            }
            const rows = { ...device.periods.today.sessions }; delete rows[Object.keys(rows)[0]];
            device = { ...device, periods: { today: { ...device.periods.today, sessions: rows } } };
            await ingest(); const removed = await next();
            assert.equal(Object.keys(removed.stats.periods.today.sessions).length, 7);
            // Explicit mutations recover with a complete frame, never a privacy-sensitive partial state.
            if (kind === "node") hub.setSubscriptions([], ""); else await hub.broadcast("subscriptions");
            assert.equal((await next()).frame.event, "stats");
            await mkdir(output, { recursive: true });
            const fixtures = records.map(({ expected, ...record }) => ({ ...record,
                expectedSha256: createHash("sha256").update(JSON.stringify(canonical(expected))).digest("hex") }));
            await writeFile(join(output, `v4-${kind}.json`), JSON.stringify(fixtures, null, 2) + "\n");
            const deltas = records.filter(r => r.event === "delta");
            assert.ok(deltas.every(r => r.bytes < Buffer.byteLength(JSON.stringify({ stats: r.expected }))));
            console.log(`${kind} v4: ordered clocks, effort/null, removal and full recovery passed; delta bytes ${deltas.reduce((sum,r)=>sum+r.bytes,0)} vs equivalent full ${deltas.reduce((sum,r)=>sum+Buffer.byteLength(JSON.stringify({stats:r.expected})),0)} (synthetic).`);
        } finally {
            abort.abort();
            if (kind === "worker") for (const client of [...hub.sseClients]) hub.dropClient(client);
            await reader?.cancel().catch(() => {}); await pump?.catch(() => {});
            if (kind === "node") await hub.stop(); else for (const timer of [hub.heartbeatTimer, hub.broadcastTimer]) if (timer) clearInterval(timer);
        }
    }
}
