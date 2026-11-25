import assert from "node:assert/strict";

export async function checkV098Reads(request, now) {
    const deviceId = "synthetic-v098-desktop";
    const day = now.slice(0, 10);
    const tomorrow = new Date(Date.parse(day) + 86_400_000).toISOString();
    const headers = { "content-type": "application/json", "x-token-monitor-fixed-ranges": "1" };
    const projectLabel = "Sample release";
    const today = {
        totalTokens: 120,
        clients: { antigravity: 120 },
        sessions: { "antigravity:v098-acp": {
            client: "antigravity", sessionId: "v098-acp", totalTokens: 120, projectLabel, lastUsedAt: now,
        } },
    };
    const ingest = await request("ingest", true, {
        method: "POST", headers,
        body: JSON.stringify({ deviceId, updatedAt: now, today,
            periodWindows: { today: { key: day, endsAt: tomorrow }, timeZone: "UTC" } }),
    });
    assert.equal(ingest.status, 200);
    assert.equal((await ingest.json()).fixedRanges, 1);

    // Simulate a desktop publishing its separate Yesterday total. The Android read is still ordinary stats.
    const yesterday = new Date(Date.parse(day) - 86_400_000).toISOString().slice(0, 10);
    const revision = "00000000-0000-0000-0000-000000000098";
    const publish = await request("fixed-ranges?deviceId=" + deviceId, true, {
        method: "PUT", headers, body: JSON.stringify({
            revision, observedAt: now, page: 0, rows: [], totalPages: 0,
            ranges: { yesterday: { start: yesterday, end: yesterday, totals: { totalTokens: 900 },
                sessions: 0, projects: 0, firstPage: 0, pageCount: 0 } },
        }),
    });
    assert.equal(publish.status, 200);
    assert.equal((await publish.json()).complete, true);
    const response = await request("stats");
    assert.equal(response.status, 200);
    const stats = await response.json();
    assert.equal(stats.fixedRangeRevisions[deviceId], revision);
    const session = stats.periods.today.sessions["antigravity:v098-acp"];
    assert.equal(session.projectLabel, projectLabel);
    assert.equal(session.totalTokens, 120);
    assert.ok(!session.title);
    // Keep the fixture small while preserving the exact selected session and advertised revisions.
    return { "v098-session.json": {
        periods: { today: { sessions: { "antigravity:v098-acp": session } } },
        fixedRangeRevisions: stats.fixedRangeRevisions,
    } };
}
