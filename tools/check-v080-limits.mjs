import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import { join } from "node:path";

/** Released collectors with injected synthetic inputs only; no CLI, file or vendor access. */
export async function checkV080Limits(require, { createHub, source, secret, device, now }) {
    const cases = {};
    const responses = {};
    const save = (name, groups) => {
        const records = groups.map((providers, index) => ({ ...device, deviceId: `synthetic-${name}-${index}`,
            limits: { updatedAt: now, providers } }));
        const hub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, `${name}.json`) });
        for (const record of records) hub.ingest(record);
        const stats = hub.getStats();
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        cases[name] = { records, providers: stats.limits.providers };
        responses[`${name}.json`] = stats;
        return stats.limits.providers;
    };
    const museCollector = require("./src/shared/providers/muse/limits.js");
    const fetchMuseLimits = museCollector.fetchMuseCodeLimits || museCollector.fetchMuseLimits;
    const muse = async allowance => fetchMuseLimits({}, {
        now: () => Date.parse(now), platform: "linux", env: { PATH: "/synthetic/bin" },
        existsSync: path => path === "/synthetic/bin/muse",
        execFile: (command, args, options, callback) => {
            assert.equal(command, "/synthetic/bin/muse");
            assert.deepEqual(args, ["usage", "--json"]);
            assert.equal(options.env.MUSE_NO_AUTO_UPDATE, "1");
            const child = Object.assign(new EventEmitter(), { stdin: { end() {} }, kill() {} });
            queueMicrotask(() => callback(null, JSON.stringify({ schemaVersion: 1, allowance }), ""));
            return child;
        },
    });
    const museRow = await muse({ tier: "Muse Code High Usage", window: { usedPercent: 25 },
        weekly: { usedPercent: 80, resetsAtMs: Date.parse("2026-11-02T00:00:00Z") } });
    const museRows = save("muse-devices", [[museRow], [museRow]]);
    assert.equal(museRows.length, 2);
    assert.equal(new Set(museRows.map(row => row.sourceDeviceId)).size, 2);
    assert.deepEqual(museRows[0].windows.map(window => window.remainingPercent), [75, 20]);
    const missing = save("muse-unknown", [[await muse(null)]])[0];
    assert.equal(missing.status, "unavailable");
    assert.equal(missing.windows.length, 0);

    const { fetchOllamaLimits } = require("./src/shared/providers/ollama/limits.js");
    const ollama = async (name, payload) => {
        const row = await fetchOllamaLimits({ ollamaApiKey: `NEVER_EXPORT_KEY_${name}` }, {
            now: () => Date.parse(now), env: {},
            fetch: async (url, init) => {
                assert.equal(new URL(url).origin, "https://ollama.com");
                assert.equal(init.redirect, "manual");
                assert.equal(init.headers.Authorization, `Bearer NEVER_EXPORT_KEY_${name}`);
                let body;
                if (url.endsWith("/api/balance")) {
                    assert.equal(init.method || "GET", "GET");
                    body = payload;
                } else {
                    assert.ok(url.endsWith("/api/me"));
                    assert.equal(init.method, "POST");
                    body = { Email: "sample@example.com", Plan: "pro" };
                }
                return { status: 200, ok: true, json: async () => body };
            },
        });
        return save(name, [[row]])[0];
    };
    const credits = await ollama("ollama-credits", { included: { balance_usd: 15, allowance_usd: 20,
        period: { until: "2026-11-26T00:00:00Z" } }, purchased: { balance_usd: 3.25 } });
    assert.equal(credits.windows.find(window => window.label === "Included credits").remainingPercent, 75);
    assert.equal(credits.windows.find(window => window.metric === "spend").used, 5);
    for (const [name, balance] of [["zero", 0], ["negative", -1]]) {
        const row = await ollama(`ollama-${name}`, { purchased: { balance_usd: balance } });
        assert.equal(row.windows[0].remaining, balance);
        assert.equal(row.windows[0].showMeter, false);
        assert.equal(row.windows[0].remainingPercent, null);
    }
    const legacy = await ollama("ollama-legacy", { included: { session: { remaining_percent: 0 }, weekly: {} } });
    assert.equal(legacy.windows.length, 1);
    assert.equal(legacy.windows[0].remainingPercent, 0);
    const cookieHtml = '<span id="header-email">sample@example.com</span>'
        + '<span>Cloud Usage</span><span>pro</span><div>Monthly usage</div><span>$5 of $20 used</span>'
        + '<section><h2>Usage credits <span>pro</span></h2><span>$3.25</span>'
        + '<span>Monthly credits used</span><span>$5.00</span></section>';
    const cookie = await fetchOllamaLimits({ ollamaCookie: "session=NEVER_EXPORT_COOKIE" }, {
        env: {}, now: () => Date.parse(now), bypassValidationCache: true,
        fetch: async (url, init) => {
            assert.equal(String(url), "https://ollama.com/settings");
            assert.equal(init.headers.Cookie, "session=NEVER_EXPORT_COOKIE");
            return { status: 200, ok: true, text: async () => cookieHtml };
        },
    });
    const cookieRow = save("ollama-cookie", [[cookie]])[0];
    assert.equal(cookieRow.windows.length, 3);
    assert.equal(cookieRow.accountKey, credits.accountKey);

    const { collectLimitsOnce } = require("./src/shared/limits/collector.js");
    const accounts = ["a", "b"].map(id => ({ id, organizationId: `org-${id}` }));
    const collect = async localId => collectLimitsOnce({ limitProviders: ["claude"], claudeManagedAccounts: accounts }, {
        env: { CLAUDE_CODE_OAUTH_TOKEN: "NEVER_EXPORT_OAUTH" }, platform: "linux", now: () => Date.parse(now),
        resolveClaudeWebCredential: account => `sessionKey=sk-ant-NEVER_EXPORT_${account.id}`,
        fetch: async (url, init) => {
            const parsed = new URL(url);
            assert.ok(["https://claude.ai", "https://api.anthropic.com"].includes(parsed.origin));
            const id = init.headers.cookie?.replace("sessionKey=sk-ant-NEVER_EXPORT_", "") || localId;
            let body;
            if (parsed.pathname === "/api/organizations") body = [{ uuid: `org-${id}`, name: `Sample ${id}` }];
            else if (parsed.pathname === "/api/account") body = { uuid: `account-${id}`, email_address: `${id}@example.com` };
            else if (parsed.pathname.endsWith("/oauth/profile")) body = {
                account: { uuid: `account-${id}`, email: `${id}@example.com` }, organization: { uuid: `org-${id}` } };
            else if (parsed.pathname.endsWith("/usage")) body = { five_hour: { utilization: id === "a" ? 10 : 20 } };
            else if (parsed.pathname.endsWith("/prepaid/credits")) body = { amount: 0, currency: "USD" };
            else throw new Error(`Unexpected synthetic Claude endpoint: ${parsed.pathname}`);
            return { status: 200, ok: true, headers: { get: () => "", getSetCookie: () => [] }, json: async () => body };
        },
    });
    const claude = save("claude-accounts", [[...(await collect("c")).providers]]);
    assert.deepEqual(claude.map(row => row.accountEmail).sort(), ["a@example.com", "b@example.com", "c@example.com"]);
    const deduped = save("claude-deduped", [[...(await collect("a")).providers]]);
    assert.equal(deduped.length, 2);
    return { cases, responses };
}
