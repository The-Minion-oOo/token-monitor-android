import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { usageProjection } from "./check-v082-usage.mjs";

/** Released collectors run against synthetic credentials, files and HTTP inside the isolated source export. */
export async function checkV088Data(require, { createHub, source, secret, device, now }) {
    const { fetchMistralLimits } = require("./src/shared/providers/mistral/limits.js");
    const { fetchMinimaxLimits, MINIMAX_CODE_MEMBERSHIP_URL_CN, MINIMAX_CODE_PLAN_REMAINS_URL_CN } =
        require("./src/shared/providers/minimax/limits.js");
    const { CLIENT_LABELS } = require("./src/shared/clientCatalog.js");
    const { sumTokens, sumOutputTokens } = require("./src/shared/history.js");
    const { publicLimits } = require("./src/shared/limits/core.js");
    const cases = {}, responses = {};
    const save = (name, groups) => {
        const records = groups.map((providers, index) => ({ ...device, deviceId: name + index,
            limits: { updatedAt: now, providers } }));
        const hub = createHub({ secret, dataFile: join(source, name + ".json") });
        for (const record of records) hub.ingest(record);
        const stats = hub.getStats();
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        cases[name] = { records, providers: stats.limits.providers };
        responses[name + ".json"] = stats;
        return stats;
    };
    const response = (body, status = 200) => ({
        ok: status === 200, status, headers: new Headers(),
        text: async () => body, json: async () => body,
    });
    const budget = { usage_percentage: 25, initial_budget: 20, currency: "EUR", reset_at: "2026-12-01T00:00:00Z" };
    const flight = '<script>self.__next_f.push(' + JSON.stringify([1, "a:" + JSON.stringify({
        budget: { api_budget: budget, vibe_budget: { ...budget, usage_percentage: 0 } },
    }) + "\n"]) + ')</script>';
    for (const [name, amount, allowanceStatus] of [
        ["mistral-allowances", 13, 200], ["mistral-zero", 0, 503], ["mistral-negative", -2, 503],
    ]) {
        const provider = await fetchMistralLimits({ mistralCookie: "ory_session_test=NEVER_EXPORT_COOKIE" }, {
            env: {}, now: () => Date.parse(now), fetch: async (url, init) => {
                assert.equal(init.headers.Cookie, "ory_session_test=NEVER_EXPORT_COOKIE");
                assert.equal(init.credentials, "omit");
                assert.equal(init.redirect, "manual");
                if (url === "https://admin.mistral.ai/subscription") return response(flight, allowanceStatus);
                assert.equal(url, "https://admin.mistral.ai/api/billing/credits");
                return response({ wallet_amount: amount, currency: "EUR" });
            },
        });
        assert.equal(provider.status, "ok");
        save(name, [[provider]]);
    }

    const base = join(source, "synthetic-minimax");
    const authRoot = join(base, "auth");
    const account = createHash("sha256").update(authRoot + "\0mcode-public").digest("base64url");
    const recordKey = "com.minimax.mcode.oauth.prod.cn\0" + account;
    const dir = join(authRoot, "prod", "cn", "mcode-public");
    mkdirSync(dir, { recursive: true, mode: 0o700 });
    const record = { schemaVersion: 1, accessToken: "NEVER_EXPORT_ACCESS", refreshToken: "NEVER_EXPORT_REFRESH",
        tokenType: "Bearer", clientId: "mcode-public", scopes: ["agent.default"], audience: "agent-backend",
        expiresAtMs: Date.parse(now) + 3600000, generation: 7, loginEpoch: "synthetic-epoch" };
    writeFileSync(join(dir, "auth.json"), JSON.stringify({ schemaVersion: 1, records: { [recordKey]: record } }), { mode: 0o600 });
    writeFileSync(join(dir, "auth-state.json"), JSON.stringify({
        schemaVersion: 2, status: "authenticated", storeKind: "file", clientId: record.clientId,
        scopes: record.scopes, audience: record.audience, buildEnv: "prod", region: "cn",
        generation: record.generation, expiresAtMs: record.expiresAtMs,
    }), { mode: 0o600 });
    const profiles = {
        first: { apiKey: "NEVER_EXPORT_API_A", billingMode: "api", apiRegion: "intl" },
        second: { apiKey: "NEVER_EXPORT_API_B", billingMode: "api", apiRegion: "intl" },
    };
    const providers = await fetchMinimaxLimits({ minimaxApiProfiles: profiles }, {
        env: { MINIMAX_DATA_DIR: base }, now: () => Date.parse(now),
        existsSync: () => false, providerRuntimeState: new Map(),
        fetch: async (url, init) => {
            const parsed = new URL(url);
            assert.ok([new URL(MINIMAX_CODE_MEMBERSHIP_URL_CN).origin, new URL(MINIMAX_CODE_PLAN_REMAINS_URL_CN).origin, "https://api.minimaxi.com", "https://api.minimax.io"].includes(parsed.origin), url);
            if (parsed.pathname.endsWith("/matrix/api/v1/user/get_user_extra_info")) {
                return response({ workspaces: [{ workspace_type: 0, workspace_id: "synthetic-workspace" }] });
            }
            if (url === MINIMAX_CODE_MEMBERSHIP_URL_CN) {
                return response({ op_credit_summary: { total_remaining_amount: "4624" },
                    op_group_id: "synthetic-group", has_token_plan: true });
            }
            if (url === MINIMAX_CODE_PLAN_REMAINS_URL_CN) return response({
                base_resp: { status_code: 0 }, model_remains: [
                    { model_name: "general", current_interval_used_percent: "30", current_weekly_status: 3 },
                    { model_name: "video", current_interval_total_count: "20", current_interval_used_count: "3" },
                ],
            });
            if (parsed.pathname === "/account/amount") return response({ consume_cash_sum: 1.25, base_resp: { status_code: 0 } });
            assert.equal(parsed.pathname, "/account/query_balance");
            return response({ available_amount: init.headers.Authorization.includes("NEVER_EXPORT_API_A") ? 0 : -2,
                base_resp: { status_code: 0 } });
        },
    });
    const rows = Array.isArray(providers) ? providers : [providers];
    assert.equal(rows.length, 3);
    assert.ok(rows.every(row => row.status === "ok"), JSON.stringify(rows));
    const code = rows.find(row => row.source === "oauth");
    assert.equal(code.windows.find(window => window.metric === "credits").currency, "CREDITS");
    assert.equal(code.windows.find(window => window.label === "Video").remaining, 17);
    assert.equal(code.windows.find(window => window.label === "Weekly").showMeter, false);
    save("minimax-products", [rows]);
    const failed = { ...rows.find(row => row.source === "api"), status: "unavailable", updatedAt: "2020-01-01T00:00:00Z", windows: [], balance: null, balanceUsd: null };
    const retained = save("minimax-stale-account", [[failed], [code]]);
    assert.ok(retained.limits.providers.some(row => row.accountKey === failed.accountKey && row.status === "unavailable"));
    assert.ok(publicLimits({ providers: rows }).providers.every(row => !row.accountKey && !row.relatedAccountKey));

    const breakdown = { input: 10, output: 20, reasoning: 5 };
    const clients = Object.fromEntries(["mistral", "prime-agent", "jcode", "copilot"].map(client => [client, sumTokens(breakdown, client)]));
    const sessions = Object.fromEntries(Object.entries(clients).map(([client, totalTokens]) => [client + ":synthetic", {
        client, sessionId: client + "-synthetic", totalTokens, outputTokens: sumOutputTokens(breakdown, client),
        models: { "synthetic-model": totalTokens }, prompt: "NEVER_EXPORT_PROMPT",
    }]));
    assert.equal(clients.jcode, 35);
    const totalTokens = Object.values(clients).reduce((a, b) => a + b, 0);
    const period = { totalTokens, clients, sessions };
    const usageDevice = { ...device, deviceId: "new-clients", periods: { today: period, month: period, allTime: period }, history: {} };
    const usageHub = createHub({ secret, dataFile: join(source, "new-clients.json") });
    usageHub.ingest(usageDevice);
    const stats = usageHub.getStats();
    assert.equal(stats.periods.today.totalTokens, totalTokens);
    assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
    cases["new-clients"] = { records: [usageDevice], providers: stats.limits.providers, usage: usageProjection(stats.periods) };
    responses["new-clients.json"] = stats;
    responses["new-client-labels.json"] = Object.fromEntries(Object.keys(clients).map(id => [id, CLIENT_LABELS[id]]));
    return { cases, responses };
}
