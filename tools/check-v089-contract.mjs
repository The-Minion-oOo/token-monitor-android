import assert from "node:assert/strict";
import { join } from "node:path";
import { usageProjection } from "./check-v082-usage.mjs";

export async function checkV089Data(require, { createHub, source, secret, now, device }) {
    const { CLIENT_LABELS } = require("./src/shared/clientCatalog.js");
    const { sumTokens, sumOutputTokens } = require("./src/shared/history.js");
    const gemini = { input: 10, output: 20, reasoning: 5 };
    assert.equal(CLIENT_LABELS.gemini, "Gemini CLI");
    assert.equal(sumTokens(gemini, "gemini"), 35);
    assert.equal(sumOutputTokens(gemini, "gemini"), 25);

    const time = minutesAgo => new Date(Date.parse(now) - minutesAgo * 60000).toISOString();
    const conversation = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    const sessions = {
        "codex:earlier": { client: "codex", sessionId: "earlier", nativeSessionId: conversation,
            lastUsedAt: time(3), totalTokens: 90, models: { "gpt-5": 90 }, latestModel: "gpt-5" },
        "codex:continuation": { client: "codex", sessionId: "continuation", nativeSessionId: conversation,
            lastUsedAt: time(2), totalTokens: 10, models: { "gpt-5": 10 }, latestModel: "openai/gpt-5.5" },
        "codex:child": { client: "codex", sessionId: "child", parentSessionId: "continuation",
            lastUsedAt: time(1), totalTokens: 20, models: { "gpt-5-mini": 20 }, latestModel: "gpt-5-mini" },
        "claude:archived": { client: "claude", sessionId: "archived", archived: true,
            lastUsedAt: time(10), totalTokens: 100, models: { "claude-sonnet-4-6": 90, "claude-opus-4-7": 10 },
            latestModel: "claude-opus-4-7" },
        "codex:unknown": { client: "codex", sessionId: "unknown", lastUsedAt: time(20),
            totalTokens: 5, models: { "gpt-5": 5 }, latestModel: null },
        "gemini:usage": { client: "gemini", sessionId: "usage", lastUsedAt: time(20),
            totalTokens: sumTokens(gemini, "gemini"), outputTokens: sumOutputTokens(gemini, "gemini"),
            models: { "gemini-3-flash": 35 }, prompt: "NEVER_EXPORT_PROMPT" },
    };
    const clients = { codex: 125, claude: 100, gemini: 35 };
    const period = { totalTokens: 260, clients, sessions };
    const record = { ...device, deviceId: "synthetic-v089", trackedClients: Object.keys(clients),
        periods: { today: period, month: period, allTime: period }, history: {} };
    const hub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, "v089-stats.json") });
    hub.ingest(record);
    await hub.start();
    try {
        const response = await fetch(`http://127.0.0.1:${hub.server.address().port}/api/stats`, {
            headers: { Authorization: `Bearer ${secret}` }, redirect: "error", signal: AbortSignal.timeout(5000),
        });
        assert.equal(response.status, 200);
        const stats = await response.json();
        const today = stats.periods.today;
        assert.equal(today.clients.gemini, 35);
        assert.equal(today.sessions["gemini:usage"].outputTokens, 25);
        assert.equal(today.sessions["codex:continuation"].latestModel, "openai/gpt-5.5");
        assert.equal(today.sessions["codex:continuation"].nativeSessionId, conversation);
        assert.equal(today.sessions["claude:archived"].latestModel, "claude-opus-4-7");
        assert.equal(today.sessions["codex:unknown"].latestModel, null);
        assert.ok(!Object.hasOwn(today.sessions["gemini:usage"], "latestModel"));
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        return {
            cases: { "v089-stats": { records: [record], providers: stats.limits.providers, usage: usageProjection(stats.periods) } },
            responses: { "v089-stats.json": stats },
        };
    } finally {
        await hub.stop();
    }
}
