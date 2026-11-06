import assert from "node:assert/strict";
import { join } from "node:path";

export async function checkV087Limits(require, { createHub, source, secret, device, now }) {
    const { fetchOpendesignLimits, OPENDESIGN_API } = require("./src/shared/providers/opendesign/limits.js");
    const session = { durationSeconds: 18000, usedCredits: "0", limitCredits: "20", resetsAt: "2026-11-06T16:00:00Z" };
    const weekly = { durationSeconds: 604800, usedCredits: 15, limitCredits: 60, resetsAt: "invalid" };
    const cases = {}, responses = {};
    for (const [name, workspaceId, windows, status] of [
        ["opendesign-windows", "workspace-a", [weekly, session], "ok"],
        ["opendesign-weekly", "workspace-b", [weekly], "ok"],
        ["opendesign-invalid", "workspace-a", [{ ...session, limitCredits: 0 }], "unavailable"],
    ]) {
        const calls = [];
        const provider = await fetchOpendesignLimits({ opendesignCookie: "session=NEVER_EXPORT_COOKIE" }, {
            env: {}, now: () => Date.parse(now),
            fetch: async (url, init) => {
                calls.push(url);
                assert.equal(init.headers.Cookie, "session=NEVER_EXPORT_COOKIE");
                assert.equal(init.redirect, "error");
                const current = url === OPENDESIGN_API + "current";
                if (!current) assert.equal(init.headers["X-Vela-Workspace-Id"], workspaceId);
                return { status: 200, json: async () => current
                    ? { workspaceId, workspaceName: "Synthetic workspace" }
                    : { workspaceId, eligible: true, tier: "pro", windows } };
            },
        });
        assert.deepEqual(calls, [OPENDESIGN_API + "current", OPENDESIGN_API + workspaceId + "/billing/coding-plan-usage"]);
        assert.equal(provider.status, status);
        assert.ok(provider.accountKey && provider.accountKey !== workspaceId);
        const records = [{ ...device, deviceId: name, limits: { updatedAt: now, providers: [provider] } }];
        const hub = createHub({ secret, dataFile: join(source, name + ".json") });
        hub.ingest(records[0]);
        const stats = hub.getStats();
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        cases[name] = { records, providers: stats.limits.providers };
        responses[name + ".json"] = stats;
    }
    assert.notEqual(cases["opendesign-windows"].providers[0].accountKey, cases["opendesign-weekly"].providers[0].accountKey);
    return { cases, responses };
}

export function checkV087Status(require) {
    const { SERVICE_STATUS_PROVIDERS, summarizeStatuspageProvider, summarizeInstatusProvider, summarizeOpenCodeProvider } =
        require("./src/electron/serviceStatus.js");
    const providers = SERVICE_STATUS_PROVIDERS;
    const provider = id => providers.find(value => value.id === id);
    const [copilot, models] = provider("github").componentIds;
    const component = (id, status = "operational") => ({ id, name: id, status });
    const github = { status: { indicator: "critical" }, components: [component(copilot), component(models), component("actions", "major_outage")],
        incidents: [{ name: "Actions is down", status: "investigating", impact: "critical", components: [{ id: "actions" }] }] };
    const cases = [
        ["github", "unrelated", github, "ok"],
        ["github", "missing", { ...github, components: [component(copilot)] }, "unknown"],
        ["github", "scoped-outage", { ...github, incidents: [...github.incidents,
            { name: "Model availability", status: "monitoring", impact: "major", components: [{ id: models }] }] }, "outage"],
        ["github", "maintenance", { ...github, scheduled_maintenances: [
            { status: "in_progress", components: [{ id: copilot }] }, { status: "in_progress", components: [{ id: "actions" }] }] }, "ok"],
        ["zed", "healthy", { page: { status: "UP" } }, "ok"],
        ["zed", "maintenance", { page: { status: "UNDERMAINTENANCE" }, activeMaintenances: [{}] }, "ok"],
        ["zed", "unnamed-issue", { page: { status: "HASISSUES" } }, "degraded"],
        ["zed", "major-incident", { page: { status: "HASISSUES" }, activeIncidents: [{ impact: "MAJOROUTAGE", name: "Requests failing" }] }, "outage"],
        ["zed", "broken", { page: { status: "UP" }, activeIncidents: {} }, "unknown"],
        ["opencode", "operational", { status: "operational" }, "ok"],
        ["opencode", "upstream-outage", { status: "operational", components: [{ service: "API", status: "partial_outage", reason: "upstream" }] }, "outage"],
        ["opencode", "mixed-cause", { status: "operational", components: [
            { service: "API", status: "degraded", reason: "upstream" }, { service: "Web", status: "unfamiliar" }] }, "degraded"],
        ["opencode", "malformed-component", { status: "operational", components: [{ service: "API" }] }, "unknown"],
    ].map(([id, name, body, health]) => {
        const summarize = { github: summarizeStatuspageProvider, zed: summarizeInstatusProvider, opencode: summarizeOpenCodeProvider }[id];
        const result = summarize(provider(id), body, { checkedAt: "2026-11-06T12:00:00Z" });
        assert.equal(result.status, health, name);
        return { id, name, body, health, description: result.incidentTitle || result.description,
            affectedComponents: result.componentIssues.length, incidentCount: result.incidentCount, maintenanceCount: result.maintenanceCount };
    });
    return { providers, cases };
}

/** Desktop producer writes a synthetic record. Android only consumes the resulting reads. */
export async function checkV087DeltaReads(request, deviceId, version) {
    const updatedAt = new Date().toISOString();
    const date = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
    const payload = { deviceId, hostname: "Synthetic desktop", agentVersion: version, updatedAt,
        today: { totalTokens: 200, costUsd: 1, clients: { codex: 200 } },
        history: { daily: [{ date, tokens: 123, cost: 1 }], monthly: [] } };
    const post = (name, body) => request(name, true, { method: "POST", headers: {
        "content-type": "application/json", "x-token-monitor-upload": "1", "x-token-monitor-response": "minimal",
    }, body: JSON.stringify(body) });
    const full = await post("ingest", payload);
    assert.equal(full.status, 200);
    const { upload } = await full.json();
    assert.equal(upload.version, 1);
    assert.ok(upload.revision);
    const patch = [
        { op: "replace", path: "/today/totalTokens", value: 0 },
        { op: "replace", path: "/today/costUsd", value: 0 },
        { op: "replace", path: "/today/clients/codex", value: 0 },
        { op: "replace", path: "/history/daily/0/tokens", value: 321 },
    ];
    const delta = { deviceId, baseRevision: upload.revision, patch };
    const accepted = await post("ingest/delta", delta);
    assert.equal(accepted.status, 200);
    const { upload: next } = await accepted.json();
    assert.notEqual(next.revision, upload.revision);
    const rejected = await post("ingest/delta", { ...delta, patch: [{ op: "replace", path: "/today/totalTokens", value: 99 }] });
    assert.equal(rejected.status, 409);
    assert.equal((await rejected.json()).error, "upload_baseline_mismatch");
    const read = async name => {
        const response = await request(name);
        assert.equal(response.status, 200);
        return response.json();
    };
    const stats = await read("stats");
    const history = await read("history");
    assert.equal(stats.periods.today.totalTokens, 0);
    assert.equal(stats.periods.today.costUsd, 0);
    assert.equal(history.daily.find(row => row.date === date)?.tokens, 321);
    return { "delta-stats.json": stats, "delta-history.json": history };
}
