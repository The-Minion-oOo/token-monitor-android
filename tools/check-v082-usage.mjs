import assert from "node:assert/strict";
import { join } from "node:path";

export function usageProjection(periods) {
    const fields = ["totalTokens", "costUsd", "cacheReadTokens", "cacheWriteTokens", "outputTokens", "unclassifiedTokens",
        "clients", "models", "clientCacheReads", "clientCacheWrites", "clientOutputs", "clientUnclassifiedTokens",
        "modelCacheReads", "modelCacheWrites", "modelOutputs", "modelUnclassifiedTokens"];
    return Object.fromEntries(Object.entries(periods).map(([name, period]) => [name,
        Object.fromEntries(fields.map(field => [field, period[field]]))]));
}

/** Released archive/normalization code, exercised only with in-memory synthetic records. */
export function checkV082Usage(require, { createHub, source, secret, device, now }) {
    const { captureSessionUsageArchive, applySessionUsageArchive } = require("./src/shared/usage/sessionUsageArchive.js");
    const { captureArchivedClientUsage, applyArchivedClientUsage } = require("./src/shared/usage/clientUsageArchive.js");
    const { normalizePeriod } = require("./src/shared/usage.js");
    const { sessionPromptCacheForRow } = require("./src/shared/sessionLive.js");
    const cases = {}, responses = {};
    const date = new Date(now);
    const parts = {
        alpha: { totalTokens: 60, inputTokens: 20, cacheReadTokens: 20, cacheWriteTokens: 5, outputTokens: 15 },
        beta: { totalTokens: 40, inputTokens: 10, cacheReadTokens: 20, cacheWriteTokens: 5, outputTokens: 5 },
    };
    const session = { sessionId: "archive-sample", client: "opencode", totalTokens: 100, costUsd: 1,
        inputTokens: 30, cacheReadTokens: 40, cacheWriteTokens: 10, outputTokens: 20,
        models: { alpha: 60, beta: 40 }, modelTokenComponents: parts, lastUsedAt: now,
        prompt: "NEVER_EXPORT_PROMPT", messages: ["NEVER_EXPORT_MESSAGE"] };
    const period = row => normalizePeriod({ totalTokens: row.totalTokens, costUsd: row.costUsd,
        clients: { opencode: row.totalTokens }, clientCosts: { opencode: row.costUsd },
        models: row.models, clientModels: { opencode: row.models }, sessions: { sample: row } });
    const summary = row => Object.fromEntries(["today", "month", "allTime"].map(key => [key, period(row)]));
    const empty = () => ({ today: {}, month: {}, allTime: {} });
    const save = (name, periods) => {
        const record = { deviceId: "synthetic-v082-desktop", hostname: "Synthetic desktop", platform: "linux",
            agentVersion: device.agentVersion, updatedAt: now, periods };
        const hub = createHub({ secret, dataFile: join(source, `synthetic-${name}.json`) });
        hub.ingest(record);
        const stats = hub.getStats();
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        cases[name] = { records: [record], providers: stats.limits.providers, usage: usageProjection(stats.periods) };
        responses[`${name}.json`] = stats;
        return stats.periods.month;
    };
    for (const kind of ["session", "client"]) {
        const capture = row => kind === "session" ? captureSessionUsageArchive({}, summary(row), date)
            : captureArchivedClientUsage({}, summary(row), ["opencode"], date);
        const apply = (archive, live = empty()) => kind === "session" ? applySessionUsageArchive(live, archive, { now: date })
            : applyArchivedClientUsage(live, archive, { now: date, activeClients: [] });
        for (const variant of ["observed", "legacy", "partial", "invalid"]) {
            const row = structuredClone(session);
            if (variant === "legacy") delete row.modelTokenComponents;
            if (variant === "partial") row.modelTokenComponents = { alpha: {
                totalTokens: 40, inputTokens: 20, cacheReadTokens: 10, cacheWriteTokens: 5, outputTokens: 5 } };
            if (variant === "invalid") row.modelTokenComponents.alpha.inputTokens = 1000;
            const result = save(`archive-${kind}-${variant}`, apply(capture(row)));
            assert.equal(result.totalTokens, 100);
            assert.equal(result.unclassifiedTokens, 0);
            if (variant === "observed") {
                assert.deepEqual(result.modelCacheReads, { alpha: 20, beta: 20 });
                assert.deepEqual(result.modelUnclassifiedTokens, {});
            } else if (variant === "partial") assert.deepEqual(result.modelUnclassifiedTokens, { alpha: 20, beta: 40 });
            else assert.deepEqual(result.modelUnclassifiedTokens, { alpha: 60, beta: 40 });
        }
        const legacy = { ...session, models: { alpha: 100 }, modelTokenComponents: undefined };
        delete legacy.inputTokens;
        assert.equal(save(`archive-${kind}-missing-input`, apply(capture(legacy))).unclassifiedTokens, 30);
    }
    const archive = captureArchivedClientUsage({}, summary(session), ["opencode"], date);
    for (const tokens of [40, 0]) {
        const live = { ...session, totalTokens: tokens, costUsd: 0, inputTokens: tokens, cacheReadTokens: 0,
            cacheWriteTokens: 0, outputTokens: 0, models: { alpha: tokens }, modelTokenComponents: undefined };
        const combined = applyArchivedClientUsage(summary(live), archive, { now: date, activeClients: [] });
        assert.equal(save(`archive-correction-${tokens}`, combined).totalTokens, tokens);
    }
    const unrelated = { ...session, sessionId: "another-session", totalTokens: 20, costUsd: 0,
        inputTokens: 20, cacheReadTokens: 0, cacheWriteTokens: 0, outputTokens: 0, models: { alpha: 20 }, modelTokenComponents: undefined };
    assert.equal(save("archive-unrelated", applyArchivedClientUsage(summary(unrelated), archive, { now: date, activeClients: [] })).totalTokens, 120);
    const cache = { observedAt: new Date(date.getTime() - 60_000).toISOString(), ttlSeconds: 300 };
    assert.equal(sessionPromptCacheForRow({ client: "codex", promptCache: cache }, date.getTime()), null);
    assert.ok(sessionPromptCacheForRow({ client: "claude", promptCache: cache }, date.getTime()));
    const cacheRows = { today: { totalTokens: 200, sessions: Object.fromEntries(["codex", "claude"].map(client => [client,
        { sessionId: `legacy-${client}`, client, totalTokens: 100, models: { synthetic: 100 }, lastUsedAt: now,
            inputTokens: 20, cacheReadTokens: 60, cacheWriteTokens: 20, promptCache: cache }])) } };
    save("legacy-cache", cacheRows);
    return { cases, responses };
}
