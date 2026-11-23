import assert from "node:assert/strict";
import { join } from "node:path";

export async function checkV097Notion(require, { createHub, source, secret, now }) {
    const { parseNotionAllowance } = require("./src/shared/providers/notion/limits.js");
    const reset = Date.parse(now) + 86_400_000;
    const { windows } = parseNotionAllowance({
        status: "not_applicable",
        allowances: [
            { bucketId: "auto", cadence: "billing_period", state: "resolved",
                credits: { used: 134.5, limit: 1500 }, periodEndMs: reset },
            { bucketId: "flex", cadence: "billing_period", state: "resolved",
                credits: { used: 0, limit: 500 }, periodEndMs: reset },
        ],
    }, Date.parse(now));
    assert.deepEqual(windows.map(({ label, used, limit, remaining }) => [label, used, limit, remaining]),
        [["Standard", 134.5, 1500, 1365.5], ["Select", 0, 500, 500]]);
    const record = {
        deviceId: "synthetic-v097-notion", updatedAt: now,
        limits: { providers: [{ provider: "notion", source: "web", accountKey: "demo-v097-notion",
            status: "ok", updatedAt: now, windows }] },
    };
    const hub = createHub({ host: "127.0.0.1", port: 0, secret, deployment: "standalone",
        dataFile: join(source, "v097-notion-store.json") });
    hub.ingest(record);
    await hub.start();
    try {
        const base = `http://127.0.0.1:${hub.server.address().port}/api`;
        const healthResponse = await fetch(`${base}/health`, { signal: AbortSignal.timeout(5000), redirect: "error" });
        assert.equal(healthResponse.status, 200);
        const health = await healthResponse.json();
        assert.equal(health.runtime, "node-hub");
        assert.equal(health.deployment, "standalone");
        const response = await fetch(`${base}/stats`, {
            headers: { Authorization: `Bearer ${secret}` },
            signal: AbortSignal.timeout(5000), redirect: "error",
        });
        assert.equal(response.status, 200);
        const stats = await response.json();
        const quotas = stats.limits.providers[0].windows;
        assert.deepEqual(quotas.map(({ label, used, limit, remaining, resetsAt }) => [label, used, limit, remaining, resetsAt]),
            windows.map(({ label, used, limit, remaining, resetsAt }) => [label, used, limit, remaining, resetsAt]));
        return { record, stats, health };
    } finally {
        await hub.stop();
    }
}
