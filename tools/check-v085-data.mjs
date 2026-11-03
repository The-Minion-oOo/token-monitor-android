import assert from "node:assert/strict";
import { mkdirSync, writeFileSync, unlinkSync } from "node:fs";
import { join } from "node:path";

/** Execute released normalization/title readers against a disposable synthetic home only. */
export async function checkV085Data(require, { createHub, source, secret, device, now }) {
    const cases = {}, responses = {};
    const titleMap = stats => Object.fromEntries(Object.entries(stats.periods.today.sessions || {}).map(([key, session]) => [key, session.title || ""]));
    const kinds = stats => Object.fromEntries(stats.devices.map(row => [row.deviceId, row.deviceKind ?? null]));
    const save = (name, records, titleSharing) => {
        const hub = createHub({ secret, syncSessionTitles: Boolean(titleSharing), dataFile: join(source, `${name}.json`) });
        const policies = new Map();
        for (const row of records) {
            if (titleSharing === "on" || titleSharing === "revoked") {
                if (!policies.has(row.deviceId)) policies.set(row.deviceId, hub.setSyncTitlePolicy(row.deviceId, true));
            }
            hub.ingest({ ...row, ...(policies.has(row.deviceId) ? { sessionTitleSyncGeneration: policies.get(row.deviceId).generation } : {}) });
        }
        if (titleSharing === "revoked") for (const id of policies.keys()) hub.setSyncTitlePolicy(id, false);
        const stats = hub.getStats();
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"), `${name} leaked conversation content`);
        cases[name] = { records, providers: stats.limits.providers, deviceKinds: kinds(stats),
            ...(titleSharing ? { titleSharing, sessionTitles: titleMap(stats) } : {}) };
        responses[`${name}.json`] = stats;
        return stats;
    };
    const { DEVICE_KINDS, normalizeDeviceKind } = require("./src/shared/deviceKind.js");
    assert.equal(normalizeDeviceKind("unknown"), "computer");
    const hardware = save("hardware-kinds", DEVICE_KINDS.map((kind, index) => ({ ...device,
        deviceId: `synthetic-hardware-${index}`, hostname: `Sample ${kind}`, deviceKind: kind })));
    assert.deepEqual(hardware.devices.map(row => row.deviceKind).sort(), [...DEVICE_KINDS].sort());
    const laptop = { ...device, deviceId: "synthetic-hardware-legacy", deviceKind: "laptop" };
    const old = { ...device, deviceId: laptop.deviceId, updatedAt: new Date(Date.parse(now) + 1000).toISOString() };
    delete old.deviceKind;
    assert.equal(save("hardware-retained", [laptop, old]).devices[0].deviceKind, "laptop");
    assert.equal(save("hardware-reset", [laptop, { ...old, deviceKind: "computer" }]).devices[0].deviceKind, "computer");
    assert.equal(save("hardware-unknown", [{ ...old, deviceKind: "unknown" }]).devices[0].deviceKind, "computer");
    assert.equal(save("hardware-legacy", [old]).devices[0].deviceKind, undefined);

    const home = join(source, "synthetic-v085-home");
    const projects = join(home, ".claude", "projects", "sample");
    const store = join(home, "Library", "Application Support", "Claude", "claude-code-sessions", "account", "org");
    mkdirSync(projects, { recursive: true }); mkdirSync(store, { recursive: true });
    writeFileSync(join(projects, "remote.jsonl"), `${JSON.stringify({ type: "user", message: { content: "NEVER_EXPORT_TRANSCRIPT" } })}\n`);
    writeFileSync(join(projects, "local.jsonl"), `${JSON.stringify({ type: "custom-title", customTitle: "Transcript title" })}\n`);
    const desktopFile = join(store, "local_remote.json");
    writeFileSync(desktopFile, JSON.stringify({ cliSessionId: "remote", title: "  Desktop   title " }));
    writeFileSync(join(store, "local_native.json"), JSON.stringify({ cliSessionId: "local", title: "Old Desktop title" }));
    const { resolveSessionMetadata } = require("./src/shared/providers/claude/sessionMetadata.js");
    const context = { home, deps: { env: {}, scopedHome: true, claudeMetadataDeps: { cache: new Map() } },
        metadata: new Map(), fileSessionMetadata: (_id, _file, meta) => ({ ...meta }) };
    const resolve = () => {
        const result = resolveSessionMetadata(new Set(["remote", "local"]), context);
        for (const [id, meta] of result) context.metadata.set(`claude:${id}`, meta);
        return result;
    };
    const sessionRecord = metadata => {
        const sessions = Object.fromEntries([...metadata].map(([id, meta]) => [`claude:${id}`, {
            sessionId: id, client: "claude", title: meta.title, totalTokens: 100, costUsd: 0.01,
            models: { "claude-opus-5": 100 }, lastUsedAt: now, prompt: "NEVER_EXPORT_PROMPT", messages: ["NEVER_EXPORT_MESSAGES"],
        }]));
        const period = { totalTokens: 200, costUsd: 0.02, clients: { claude: 200 }, models: { "claude-opus-5": 200 }, sessions };
        return { ...device, deviceKind: "laptop", periods: { today: period, month: structuredClone(period), allTime: structuredClone(period) } };
    };
    let metadata = resolve();
    assert.equal(metadata.get("remote").title, "Desktop title");
    assert.equal(metadata.get("local").title, "Transcript title");
    for (const [name, sharing] of [["claude-titles-private", "off"], ["claude-titles-shared", "on"], ["claude-titles-revoked", "revoked"]]) {
        const stats = save(name, [sessionRecord(metadata)], sharing);
        assert.equal(stats.periods.today.sessions["claude:remote"].title || "", sharing === "on" ? "Desktop title" : "");
    }
    writeFileSync(desktopFile, JSON.stringify({ cliSessionId: "remote", title: "Renamed Desktop title" }));
    assert.equal(resolve().get("remote").title, "Renamed Desktop title");
    unlinkSync(desktopFile); metadata = resolve();
    assert.ok(!metadata.get("remote").title);
    assert.equal(save("claude-title-removed", [sessionRecord(metadata)], "on").periods.today.sessions["claude:remote"].title || "", "");

    const entries = [["parent", "codex", 100, "Release plan"], ["child", "claude", 40, "Write tests"],
        ["solo", "claude", 60, "Review changes"], ["workspace-free", "copilot", 20, "Editor chat"], ["memory", "senpi", 30, "Project memory"]];
    const sessions = Object.fromEntries(entries.map(([id, client, tokens, title]) => [`${client}:${id}`, {
        sessionId: id, client, title, totalTokens: tokens, costUsd: tokens / 10000, models: { "sample-model": tokens }, lastUsedAt: now,
        ...(id === "child" ? { parentSessionId: "parent", parentClient: "codex" } : {}),
    }]));
    const period = { totalTokens: 250, costUsd: 0.025, clients: { codex: 100, claude: 100, copilot: 20, senpi: 30 }, models: { "sample-model": 250 }, sessions };
    save("session-tools", [{ ...device, periods: { today: period, month: structuredClone(period), allTime: structuredClone(period) } }], "on");
    return { cases, responses };
}
