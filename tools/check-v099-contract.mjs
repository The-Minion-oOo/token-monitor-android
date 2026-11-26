import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { join } from "node:path";

export async function checkV099Reads(request, now, source) {
    const require = createRequire(join(source, "package.json"));
    const { createSyncPayloadUploader, serializeSyncPayload } = require("./src/shared/syncPayload.js");
    const deviceId = "synthetic-v099-desktop";
    const dateAt = offset => new Date(Date.parse(now) + offset * 86_400_000).toISOString().slice(0, 10);
    const day = dateAt(0);
    const yesterday = dateAt(-1);
    const dayEnd = dateAt(1) + "T00:00:00.000Z";
    const lastUsedAt = new Date(Date.parse(now) - 15 * 60_000).toISOString();
    const antigravityKey = "antigravity:v099-acp";
    const kiroKey = "kiro:v099-kiro";
    const omittedKey = "claude:v099-omitted";
    const today = {
        totalTokens: 125,
        clients: { antigravity: 100, kiro: 20, claude: 5 },
        sessions: {
            [antigravityKey]: {
                client: "antigravity", sessionId: "v099-acp", t3ThreadId: "fixture-t3", totalTokens: 100,
                projectLabel: "Sample project", lastUsedAt, turnEnded: false,
                liveActivity: { state: "running", observedAt: now },
            },
            [kiroKey]: {
                client: "kiro", sessionId: "v099-kiro", t3ThreadId: "fixture-t3", totalTokens: 20, lastUsedAt,
            },
            [omittedKey]: { client: "claude", sessionId: "v099-omitted", totalTokens: 5, lastUsedAt },
        },
    };
    const historyDay = { date: yesterday, tokens: 17, cost: 0 };
    const summary = {
        deviceId, updatedAt: now, today, month: today, allTime: { totalTokens: today.totalTokens },
        periodWindows: { today: { key: day, endsAt: dayEnd }, timeZone: "UTC" },
        nativeRangeWindows: { yesterday: { start: yesterday, end: yesterday } },
        nativeRanges: { yesterday: { totalTokens: historyDay.tokens } },
        dailySessionPeriods: Object.fromEntries(Array.from({ length: 31 }, (_, i) => {
            const date = dateAt(i - 30);
            return [date, date === day ? today : { totalTokens: date === yesterday ? historyDay.tokens : 0 }];
        })),
        history: { daily: [historyDay], dailyTotals: [historyDay], summary: { activeDays: 1, activeDaysComplete: true } },
    };
    const write = (path, body, headers = {}) => request(path, true, {
        method: "POST", headers: { "content-type": "application/json", ...headers }, body: JSON.stringify(body),
    });
    const upload = createSyncPayloadUploader();
    const { response: published } = await upload((url, init) => {
        const { pathname, search } = new URL(url);
        return request(pathname.slice("/api/".length) + search, true, {
            ...init, headers: Object.fromEntries(new Headers(init.headers)),
        });
    }, "http://127.0.0.1/api/ingest", { summary });
    assert.equal(published.status, 200);
    assert.equal((await published.json()).dailySessionRanges, 1);

    const legacy = serializeSyncPayload(summary).payload;
    const completeHistory = legacy.history;
    legacy.history = {
        ...completeHistory, dailyTotalsOmitted: 1,
        summary: { ...completeHistory.summary, activeDaysComplete: false },
    };
    delete legacy.history.dailyTotals;
    delete legacy.history.dailyTotalsRevision;
    delete legacy.today.sessions[omittedKey];
    delete legacy.month.sessions[omittedKey];
    legacy.sessionDetailsOmitted = { today: 1, month: 1 };
    const inline = await write("ingest", legacy, { "x-token-monitor-upload": "1" });
    assert.equal(inline.status, 200);
    const { upload: baseline } = await inline.json();
    const compact = await write("ingest/compact", {
        deviceId, baseRevision: baseline.revision,
        patch: [
            { op: "replace", path: "/history", value: completeHistory },
            { op: "remove", path: "/today/sessions" },
            { op: "remove", path: "/month/sessions" },
        ],
    });
    assert.equal(compact.status, 200, "Hub accepts a compact upload after publishing the daily revision");
    await compact.text();

    const [statsResponse, devicesResponse] = await Promise.all([request("stats"), request("devices")]);
    assert.equal(statsResponse.status, 200);
    assert.equal(devicesResponse.status, 200);
    const stats = await statsResponse.json();
    const { devices } = await devicesResponse.json();
    const device = devices.find(entry => entry.deviceId === deviceId);
    assert.ok(stats.fixedRangeRevisions[deviceId]);
    assert.equal(device.history.summary.activeDaysComplete, true);
    assert.equal(device.history.summary.activeDays, 1);
    assert.equal(device.history.dailyTotals[0].tokens, historyDay.tokens);
    assert.equal(device.sessionDetailsOmitted.today, 1);
    const sessions = stats.periods.today.sessions;
    assert.equal(sessions[omittedKey], undefined);
    assert.deepEqual(sessions[antigravityKey].liveActivity, today.sessions[antigravityKey].liveActivity);
    assert.equal(sessions[antigravityKey].turnEnded, false);
    assert.equal(sessions[antigravityKey].t3ThreadId, sessions[kiroKey].t3ThreadId);

    // Project the selected synthetic responses so older compatibility cases stay in their own fixtures.
    return {
        "v099-session.json": { periods: { today: { sessions: {
            [antigravityKey]: sessions[antigravityKey], [kiroKey]: sessions[kiroKey],
        } } }, fixedRangeRevisions: { [deviceId]: stats.fixedRangeRevisions[deviceId] } },
        "v099-devices.json": { devices: [device] },
    };
}
