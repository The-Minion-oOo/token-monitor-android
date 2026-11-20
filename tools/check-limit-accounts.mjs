import assert from "node:assert/strict";

export async function checkLimitAccountReads(hub, request, now) {
    const kind = "limitAccountPresentation";
    const path = `sync/settings/${kind}`;
    assert.equal((await request(path, false)).status, 401);
    const initial = await request(path);
    assert.equal(initial.status, 200);
    const empty = await initial.json();
    assert.equal(empty.revision, 0);
    assert.equal(empty.value, null);

    const value = { deepseek: { labels: { "demo-personal": "Personal", "demo-work": "Work" },
        order: ["demo-work", "demo-personal"] },
        factory: { labels: { "factory-team": "Team", "factory-solo": "Solo" },
            order: ["factory-solo", "factory-team"] },
    };
    await hub.setSyncSettings(kind, { baseRevision: 0, value });
    const response = await request(path);
    assert.equal(response.status, 200);
    const presentation = await response.json();
    assert.equal(presentation.revision, 1);
    assert.deepEqual(presentation.value, value);

    const row = (provider, accountKey, extra = {}) => ({
        provider, accountKey, status: "ok", updatedAt: now, ...extra,
    });
    const report = {
        deviceId: "synthetic-quota-desktop", updatedAt: now,
        limits: { multiAccountProviders: ["deepseek"], providers: [
            row("deepseek", "demo-personal", { source: "api", windows: [{ kind: "billing", metric: "credits", remaining: 24, currency: "USD" }] }),
            row("deepseek", "demo-work", { source: "api", status: "unavailable" }),
            row("factory", "factory-team", { source: "api", planLabel: "Team",
                windows: [{ kind: "weekly", usedPercent: 30 }] }),
            row("alibaba", "demo-ali", { source: "cli", region: "cn-personal", planLabel: "Pro" }),
            row("alibaba", "demo-ali", { source: "web", region: "intl", planLabel: "Team" }),
            row("opencode", "demo-code", { source: "web", windows: [
                { kind: "weekly", label: "Weekly", usedPercent: 30, productId: "go" },
                { kind: "billing", label: "Balance", metric: "credits", remaining: 5, currency: "USD", productId: "zen" },
            ] }),
        ] },
    };
    const second = {
        deviceId: "synthetic-quota-laptop", updatedAt: now,
        limits: { providers: [
            row("factory", "factory-solo", { source: "api", planLabel: "Pro",
                windows: [{ kind: "weekly", usedPercent: 10 }] }),
        ] },
    };
    for (const device of [report, second]) {
        const ingest = await request("ingest", true, {
            method: "POST",
            headers: { "content-type": "application/json", "x-token-monitor-response": "minimal" },
            body: JSON.stringify(device),
        });
        assert.equal(ingest.status, 200);
        await ingest.text();
    }

    const stats = await (await request("stats")).json();
    assert.equal(stats.syncSettingsRevisions[kind], 1);
    assert.equal(stats.limits.multiAccountProviders, undefined);
    const factory = stats.limits.providers.filter(row => row.provider === "factory");
    assert.deepEqual(Object.fromEntries(factory.map(row => [row.accountKey, row.sourceDeviceId])), {
        "factory-team": report.deviceId, "factory-solo": second.deviceId,
    });
    const providers = stats.limits.providers.filter(row => row.sourceDeviceId === report.deviceId);
    const deepseek = providers.filter(row => row.provider === "deepseek");
    assert.deepEqual(Object.fromEntries(deepseek.map(row => [row.accountKey, row.status])),
        { "demo-personal": "ok", "demo-work": "unavailable" });
    const alibaba = providers.filter(row => row.provider === "alibaba");
    assert.deepEqual(alibaba.map(row => row.region).sort(), ["cn-personal", "intl"]);
    assert.deepEqual(providers.find(row => row.provider === "opencode").windows.map(row => row.productId), ["go", "zen"]);
    return {
        "limit-account-presentation.json": presentation,
        "limit-account-stats.json": stats,
    };
}
