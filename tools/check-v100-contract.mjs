import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { join } from "node:path";

export async function checkV100Reads(request, now, source) {
    const require = createRequire(join(source, "package.json"));
    const { serializeSyncPayload } = require("./src/shared/syncPayload.js");
    const deviceId = "synthetic-v100-desktop";
    const contextKey = "antigravity:v100-context";
    const noContextKey = "antigravity:v100-no-context";
    const modelKey = "codex:v100-work";
    const ampKey = "amp:v100-synced";
    const lastUsedAt = now;
    const today = {
        totalTokens: 156,
        clients: { antigravity: 100, amp: 31, codex: 25 },
        sessions: {
            [contextKey]: {
                client: "antigravity", sessionId: "v100-context", totalTokens: 90, lastUsedAt,
                contextTokens: 64000, contextWindow: 256000, turnEnded: false,
            },
            [noContextKey]: { client: "antigravity", sessionId: "v100-no-context", totalTokens: 10, lastUsedAt },
            [modelKey]: {
                client: "codex", sessionId: "v100-work", totalTokens: 25, lastUsedAt,
                models: { "gpt-5.6-sol": 25 }, latestModel: "gpt-5.6-sol",
                usageSource: "codex-dots-local", usageCoverage: "observed-only",
            },
            [ampKey]: { client: "amp", sessionId: "v100-synced", totalTokens: 31, lastUsedAt },
        },
    };
    const summary = {
        deviceId, updatedAt: now, today, month: today,
        nativeSessions: { reasonix: [{ id: "LOCAL_REASONIX_SESSION" }] },
        nativeProjects: { reasonix: [{ label: "LOCAL_REASONIX_PROJECT" }] },
    };
    async function publish(value) {
        const { payload } = serializeSyncPayload(value);
        assert.ok(!Object.hasOwn(payload, "nativeSessions"));
        assert.ok(!Object.hasOwn(payload, "nativeProjects"));
        const ingest = await request("ingest", true, {
            method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(payload),
        });
        assert.equal(ingest.status, 200);
        await ingest.text();
        const read = await request("stats");
        assert.equal(read.status, 200);
        const stats = await read.json();
        assert.ok(!JSON.stringify(stats).includes("LOCAL_REASONIX_"));
        return stats.periods.today;
    }

    const live = await publish(summary);
    const sessions = live.sessions;
    for (const key of Object.keys(today.sessions)) assert.ok(sessions[key], key);
    assert.equal(sessions[contextKey].contextTokens, 64000);
    assert.equal(sessions[contextKey].contextWindow, 256000);
    assert.ok(!sessions[noContextKey].contextWindow);
    assert.equal(sessions[modelKey].latestModel, "gpt-5.6-sol");
    assert.equal(sessions[modelKey].usageCoverage, "observed-only");
    assert.equal(sessions[ampKey].totalTokens, 31);
    assert.equal(live.clients.amp, 31);

    const clearedToday = {
        ...today,
        sessions: { ...today.sessions, [contextKey]: { ...today.sessions[contextKey], contextTokens: 0, contextWindow: 0 } },
    };
    const cleared = await publish({
        ...summary, updatedAt: new Date(Date.parse(now) + 1).toISOString(), today: clearedToday, month: clearedToday,
    });
    assert.equal(cleared.sessions[contextKey].contextTokens ?? 0, 0);
    assert.equal(cleared.sessions[contextKey].contextWindow ?? 0, 0);

    // Save only the selected synthetic rows, not unrelated devices in the shared harness.
    return {
        "v100-session.json": { periods: { today: { clients: { amp: live.clients.amp },
            sessions: Object.fromEntries(Object.keys(today.sessions).map(key => [key, sessions[key]])) } } },
        "v100-cleared-context.json": { periods: { today: { sessions: { [contextKey]: cleared.sessions[contextKey] } } } },
    };
}
