import assert from "node:assert/strict";

// Setup uses only a disposable Hub's internal setter. Android consumes authenticated GETs.
export async function checkModelAliasReads(hub, request) {
    const path = "sync/settings/modelAliases";
    assert.equal((await request(path, false)).status, 401);
    const empty = await (await request(path)).json();
    assert.equal(empty.revision, 0);
    assert.equal(empty.value, null);
    const value = { modelAliases: { "provider/gpt-5": "GPT Five", "gpt-5": "GPT Five" }, modelAliasGrouping: "duplicates" };
    await hub.setSyncSettings("modelAliases", { baseRevision: 0, value });
    const response = await request(path);
    assert.equal(response.status, 200);
    const settings = await response.json();
    assert.equal(settings.ok, true);
    assert.equal(settings.version, 1);
    assert.equal(settings.revision, 1);
    assert.deepEqual(settings.value, value);
    const stats = await (await request("stats")).json();
    assert.equal(stats.syncSettingsRevisions.modelAliases, settings.revision);
    return { "model-aliases-empty.json": empty, "model-aliases.json": settings, "model-aliases-stats.json": stats };
}

export function checkModelAliasResolver(require) {
    const { createModelAliasResolver } = require("./src/electron/renderer/modelAliases.js");
    const ids = ["provider/gpt-5", "gpt-5", "OTHER/GPT.5", "vendor/solo", "vendor/gpt-5-cc", "gpt--5", "a/b/Claude_Sonnet", "claude-sonnet", "provider/gpt\u00a05", "\uFEFFgpt-5\uFEFF"];
    return ["off", "duplicates", "prefix"].flatMap(grouping => [
        {},
        { "gpt-5": "GPT Five", "provider/gpt-5": "Private name", "GPT Five": "One hop only" },
        { "claude-sonnet": "Sonnet", "vendor/solo": "Solo" },
    ].map(aliases => {
        const resolve = createModelAliasResolver(aliases, ids, grouping);
        return { grouping, aliases, ids, expected: ids.map(resolve) };
    }));
}
