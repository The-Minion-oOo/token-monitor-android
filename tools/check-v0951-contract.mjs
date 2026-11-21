import assert from "node:assert/strict";

export async function checkV0951Reads(request, now) {
    const mainId = "synthetic-v0951-desktop";
    const laptopId = "synthetic-v0951-laptop";
    const earlier = new Date(Date.parse(now) - 1_000).toISOString();
    const row = (provider, accountKey, usedPercent, extra) => ({
        provider, accountKey, source: "api", status: "ok", updatedAt: now,
        windows: [{ kind: "weekly", usedPercent }], ...extra,
    });
    const session = (client, id, totalTokens, contextTokens, contextWindow) => ({
        client, sessionId: id, totalTokens, lastUsedAt: now, contextTokens, contextWindow,
    });
    const sessions = {
        "junie:junie-demo": session("junie", "junie-demo", 70),
        "opencodereview:review-demo": session("opencodereview", "review-demo", 60),
        "grok:grok-demo": session("grok", "grok-demo", 30, 30, 100),
        "workbuddy:workbuddy-demo": session("workbuddy", "workbuddy-demo", 20, 40, 100),
        "catpaw:catpaw-demo": session("catpaw", "catpaw-demo", 10, 50, 100),
    };
    const clients = { junie: 70, opencodereview: 60, grok: 30, workbuddy: 20, catpaw: 10 };
    const period = { totalTokens: 190, clients, sessions };
    const devices = [{
        deviceId: mainId, updatedAt: now, trackedClients: Object.keys(clients),
        periods: { today: period, month: period, allTime: period },
        limits: { providers: [
            row("qoder", "demo-qoder-a", 25, { source: "web", region: "global",
                accountKeyPortable: true, accountKeyAliases: ["demo-qoder-cookie"] }),
            row("zaiteam", "demo-project-a", 20, { updatedAt: earlier }),
        ] },
    }, {
        deviceId: laptopId, updatedAt: now,
        limits: { providers: [
            row("qoder", "demo-qoder-a", 35, { source: "web", region: "cn" }),
            row("qoder", "demo-qoder-b", 45, { source: "web", region: "global" }),
            row("qoder", "demo-qoder-cookie", 55, { source: "web", region: "global" }),
            row("zaiteam", "demo-project-a", 30),
            row("zaiteam", "demo-project-b", 40),
        ] },
    }];
    for (const device of devices) {
        const response = await request("ingest", true, {
            method: "POST",
            headers: { "content-type": "application/json", "x-token-monitor-response": "minimal" },
            body: JSON.stringify(device),
        });
        assert.equal(response.status, 200);
        await response.text();
    }
    const response = await request("stats");
    assert.equal(response.status, 200);
    const stats = await response.json();
    const today = stats.periods.today;
    for (const [client, tokens] of Object.entries(clients)) assert.equal(today.clients[client], tokens);
    assert.equal(today.clients.opencode, undefined);
    for (const [key, expected] of Object.entries(sessions)) {
        const actual = today.sessions[key];
        assert.equal(actual.client, expected.client);
        assert.equal(actual.totalTokens, expected.totalTokens);
        if (expected.contextWindow) {
            assert.equal(actual.contextTokens, expected.contextTokens);
            assert.equal(actual.contextWindow, expected.contextWindow);
        }
    }
    const providers = stats.limits.providers.filter(account => [mainId, laptopId].includes(account.sourceDeviceId));
    const qoder = providers.filter(row => row.provider === "qoder");
    assert.equal(qoder.length, 3);
    assert.deepEqual(Object.fromEntries(qoder.map(row => [
        `${row.accountKey}:${row.region}`, row.windows[0].usedPercent,
    ])), { "demo-qoder-a:global": 25, "demo-qoder-a:cn": 35, "demo-qoder-b:global": 45 });
    const team = providers.filter(row => row.provider === "zaiteam");
    assert.equal(team.length, 2);
    assert.deepEqual(Object.fromEntries(team.map(row => [row.accountKey, row.windows[0].usedPercent])),
        { "demo-project-a": 30, "demo-project-b": 40 });
    return { "v0951-compatibility.json": stats };
}
