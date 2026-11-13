import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { writeFile } from "node:fs/promises";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

export async function checkV092Limits(require, { createHub, source, secret, device, now }) {
    const { fetchCopilotLimits } = require("./src/shared/providers/copilot/limits.js");
    const { fetchDeepSeekLimits } = require("./src/shared/providers/deepseek/limits.js");
    const { publicLimits } = require("./src/shared/limits/core.js");
    const copilot = await fetchCopilotLimits({ copilotProfiles: {
        personal: { token: "NEVER_EXPORT_COPILOT_A", accountKey: "synthetic-personal", accountName: "Demo Personal" },
        work: { token: "NEVER_EXPORT_COPILOT_B", accountKey: "synthetic-work", accountName: "Demo Work" },
    } }, { env: {}, now: () => Date.parse(now), fetch: async (url, init) => {
        const work = init.headers.Authorization.endsWith("_B");
        if (!url.includes("copilot_internal")) return Response.json({ login: work ? "Demo Work" : "Demo Personal" });
        return work ? new Response(null, { status: 401 }) : Response.json({
            copilot_plan: "pro",
            quota_snapshots: { premium_interactions: { entitlement: 100, remaining: 75, percent_remaining: 75 } },
        });
    } });
    assert.deepEqual(copilot.map(row => row.status), ["ok", "unauthorized"]);
    const deepseek = await fetchDeepSeekLimits({
        deepseekApiKey: "NEVER_EXPORT_DEEPSEEK_KEY", deepseekWebToken: "NEVER_EXPORT_DEEPSEEK_CONSOLE",
    }, { env: {}, now: () => Date.parse(now), deepseekStorePath: join(source, "synthetic-deepseek-spend.json"),
        fetch: async url => {
            const endpoint = new URL(url);
            if (endpoint.hostname === "api.deepseek.com") {
                return Response.json({ balance_infos: [{ currency: "CNY", total_balance: "10", topped_up_balance: "10" }] });
            }
            assert.equal(endpoint.hostname, "platform.deepseek.com");
            return Response.json({ code: 0, data: { biz_code: 0, biz_data: {
                start: +endpoint.searchParams.get("start"), end: +endpoint.searchParams.get("end"),
                data: [{ currency: "CNY", series: [
                    { model: "server-tool-search", buckets: [{ cost: 3 }] },
                    { model: "server-tool-fetch", buckets: [{ cost: 0.02 }] },
                ] }],
            } } });
        },
    });
    assert.equal(deepseek.status, "ok");
    const first = { ...device, agentRuntime: "electron-widget", limits: { updatedAt: now, providers: [...copilot, deepseek] } };
    const second = { ...device, agentRuntime: "headless-agent", limits: {
        updatedAt: new Date(Date.parse(now) + 1).toISOString(),
        providers: [{ provider: "copilot", status: "notConfigured" }, deepseek],
    } };
    const hub = createHub({ host: "127.0.0.1", port: 0, secret, dataFile: join(source, "v092-limits-store.json") });
    hub.ingest(first);
    hub.ingest(second);
    await hub.start();
    try {
        const response = await fetch(`http://127.0.0.1:${hub.server.address().port}/api/stats`, {
            headers: { Authorization: `Bearer ${secret}` }, signal: AbortSignal.timeout(5000), redirect: "error",
        });
        assert.equal(response.status, 200);
        const stats = await response.json();
        assert.equal(stats.limits.providers.filter(row => row.provider === "copilot").length, 2);
        const balance = stats.limits.providers.find(row => row.provider === "deepseek").balance;
        assert.equal(balance.searchSpend, 3);
        assert.equal(balance.fetchSpend, 0.02);
        const publicBalance = publicLimits(stats.limits).providers.find(row => row.provider === "deepseek").balance;
        assert.equal(publicBalance.searchSpend, undefined);
        assert.equal(publicBalance.fetchSpend, undefined);
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        return { records: [first, second], stats };
    } finally {
        await hub.stop();
    }
}

// A stalled writer models a slow client while the released Worker creates the actual wire frames.
export async function checkV092WorkerRecovery(require, source, output) {
    const { createStatsStreamReceiver, SSE_BACKLOG_LIMIT_BYTES } = require("./src/shared/hubProtocol.js");
    const { HubDO } = await import(pathToFileURL(join(source, "worker/src/index.js")));
    const hub = new HubDO({}, { TOKEN_MONITOR_SECRET: "synthetic-stream-secret" });
    let tokens = 1;
    const padding = "x".repeat(Math.ceil(SSE_BACKLOG_LIMIT_BYTES * 0.6));
    hub.statsWithSubscriptionVersion = async () => ({ periods: { today: { totalTokens: tokens } }, devices: [], padding });
    const frames = [], pending = [];
    const client = { deltaEvents: true, freshnessEvents: true, streamRevision: null, writer: {
        write(bytes) {
            const block = new TextDecoder().decode(bytes);
            frames.push({ event: /^event: (\w+)/m.exec(block)[1], data: JSON.parse(/^data: (.+)$/m.exec(block)[1]) });
            return new Promise(resolve => pending.push(resolve));
        },
        close() {},
    } };
    const drain = async () => {
        while (pending.length) {
            pending.splice(0).forEach(resolve => resolve());
            await new Promise(setImmediate);
        }
    };
    const canonical = value => Array.isArray(value) ? value.map(canonical) : value && typeof value === "object"
        ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
    hub.sseClients.add(client);
    try {
        await hub.flushBroadcast();
        tokens = 2;
        await hub.flushBroadcast();
        assert.deepEqual(frames.map(frame => frame.event), ["stats", "delta"]);
        await drain();
        assert.equal(frames.length, 2);
        for (let i = 0; i < 3; i++) await hub.broadcast("subscriptions");
        assert.equal(client.needsSnapshot, true);
        tokens = 3;
        await hub.flushBroadcast();
        await drain();
        const recovery = frames.at(-1);
        assert.equal(recovery.event, "stats");
        assert.equal(recovery.data.reason, "snapshot");
        assert.equal(recovery.data.stats.periods.today.totalTokens, 3);
        tokens = 4;
        await hub.flushBroadcast();
        assert.equal(frames.at(-1).event, "delta");
        assert.equal(frames.at(-1).data.baseRevision, recovery.data.revision);
        await drain();
        const receiver = createStatsStreamReceiver();
        const fixture = frames.map(frame => {
            const stats = receiver.receive(frame.event, frame.data);
            return { ...frame, expectedSha256: createHash("sha256").update(JSON.stringify(canonical(stats))).digest("hex") };
        });
        await writeFile(join(output, "v092-worker-recovery.json"), JSON.stringify(fixture) + "\n");
        console.log("Worker v4: queued deltas, bounded backlog and revisioned recovery passed.");
    } finally {
        hub.dropClient(client);
        if (hub.heartbeatTimer) clearInterval(hub.heartbeatTimer);
        if (hub.broadcastTimer) clearTimeout(hub.broadcastTimer);
    }
}
