import assert from "node:assert/strict";
import { join } from "node:path";

/** Released collectors with injected credentials and responses; no account, CLI or vendor access. */
export async function checkV083Limits(require, { createHub, source, secret, device, now }) {
    const cases = {}, responses = {};
    const save = (name, provider) => {
        const record = { ...device, deviceId: `synthetic-${name}`, limits: { updatedAt: now, providers: [provider] } };
        const hub = createHub({ secret, dataFile: join(source, `${name}.json`) });
        hub.ingest(record);
        const stats = hub.getStats();
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        cases[name] = { records: [record], providers: stats.limits.providers };
        responses[`${name}.json`] = stats;
        return stats.limits.providers[0];
    };
    const { fetchClaudeLimits } = require("./src/shared/providers/claude/limits.js");
    const { collectLimitsOnce } = require("./src/shared/limits/collector.js");
    for (const [name, subscriptionType, rateLimitTier, expected] of [
        ["max", "max", "default_claude_max_5x", "Max"],
        ["ultra", "max", "default_claude_max_20x", "Ultra"],
        ["team", "team", "default_claude_max_20x", "Team"],
    ]) {
        const row = await fetchClaudeLimits({}, {
            platform: "linux", env: {}, now: () => Date.parse(now),
            claudeCredentialPath: "/synthetic/claude-credentials.json",
            stat: async () => ({ mtimeMs: 1 }),
            readFile: async () => JSON.stringify({ claudeAiOauth: { accessToken: "NEVER_EXPORT_OAUTH", subscriptionType, rateLimitTier } }),
            fetch: async url => {
                assert.equal(new URL(url).origin, "https://api.anthropic.com");
                return { ok: true, json: async () => url.endsWith("/api/oauth/profile")
                    ? { account: { uuid: `sample-${name}`, email: `${name}@example.com` }, organization: { uuid: `org-${name}` } }
                    : { five_hour: { utilization: 25 } } };
            },
        });
        assert.equal(save(`claude-${name}`, row).accountLabel, expected);
    }
    for (const [name, text, expected] of [
        ["weekly", "Current week (all models)\n65% left\nResets Nov 2, 6pm\nPlan: Ultra", 65],
        ["weekly-zero", "Current week (all models)\n0% left\nPlan: Max 5x", 0],
        ["missing", "Plan: Ultra", null],
    ]) {
        const result = await collectLimitsOnce({ limitProviders: ["claude"] }, {
            platform: "linux", env: {}, now: () => Date.parse(now),
            claudeCredentialPath: "/synthetic/no-credentials.json",
            stat: async () => { throw Object.assign(new Error("missing"), { code: "ENOENT" }); },
            readMacKeychain: false, readWindowsCredential: false,
            isClaudeCliAuthenticated: async () => true, runClaudeUsageCli: async () => text,
            fetch: async () => { throw new Error("Vendor network forbidden"); },
        });
        const exported = save(`claude-${name}`, result.providers.find(row => row.provider === "claude"));
        if (expected === null) {
            assert.notEqual(exported.status, "ok");
            assert.deepEqual(exported.windows, []);
        } else {
            assert.equal(exported.status, "ok");
            assert.deepEqual(exported.windows.map(w => w.kind), ["weekly"]);
            assert.equal(exported.windows[0].remainingPercent, expected);
            assert.equal(exported.accountLabel, expected === 0 ? "Max" : "Ultra");
        }
    }
    const { fetchTraeLimits } = require("./src/shared/providers/trae/limits.js");
    for (const [name, total, used] of [["credits", 100, 25], ["zero", 0, 0], ["overdrawn", 100, 125], ["expired", null, null]]) {
        const calls = [];
        const row = await fetchTraeLimits({ traeCookie: "sessionid=NEVER_EXPORT_COOKIE", traeAccessToken: "NEVER_EXPORT_IDE_TOKEN" }, {
            env: {}, now: () => Date.parse(now), fetch: async (url, init) => {
                calls.push(url);
                assert.equal(new URL(url).origin, "https://api.trae.cn");
                assert.equal(init.headers.Cookie, "sessionid=NEVER_EXPORT_COOKIE");
                assert.equal(init.redirect, "error");
                assert.equal(init.credentials, "omit");
                const exchange = url.endsWith("/GetUserToken");
                assert.ok(exchange || url.endsWith("/user_current_entitlement_list"));
                assert.equal(init.headers.Authorization, exchange ? undefined : "Cloud-IDE-JWT NEVER_EXPORT_JWT");
                return { ok: name !== "expired", status: name === "expired" ? 401 : 200,
                    json: async () => exchange ? { Result: { Token: "NEVER_EXPORT_JWT" } }
                        : { is_credits_billing: true, usage_summary: { total_amount: total, consumed_amount: used } } };
            },
        });
        const exported = save(`trae-${name}`, row);
        if (name === "expired") {
            assert.equal(exported.status, "unauthorized");
            assert.deepEqual(exported.windows, []);
            assert.equal(calls.length, 1);
        } else {
            assert.equal(calls.length, 2);
            assert.equal(exported.windows[0].limit, total);
            assert.equal(exported.windows[0].used, used);
            assert.equal(exported.windows[0].remaining, Math.max(0, total - used));
            assert.equal(exported.windows[0].currency, "CREDITS");
            assert.equal(exported.windows[0].resetsAt, null);
            assert.equal(exported.windows[0].remainingPercent, total === 0 ? null : Math.max(0, 100 - used));
        }
    }
    return { cases, responses };
}
