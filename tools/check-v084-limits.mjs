import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import { join } from "node:path";

/** Actual released collectors, injected inputs and responses only; no credentials or vendor access. */
export async function checkV084Limits(require, { createHub, source, secret, device, now }) {
    const cases = {}, responses = {};
    const save = (name, groups) => {
        const records = groups.map((providers, index) => ({ ...device, deviceId: `synthetic-${name}-${index}`,
            limits: { updatedAt: now, providers } }));
        const hub = createHub({ secret, staleAfterMs: 600_000, dataFile: join(source, `${name}.json`) });
        for (const record of records) hub.ingest(record);
        const stats = hub.getStats();
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"), `${name} exported credentials`);
        cases[name] = { records, providers: stats.limits.providers };
        responses[`${name}.json`] = stats;
        return stats.limits.providers;
    };
    const { fetchManusLimits } = require("./src/shared/providers/manus/limits.js");
    const claims = Buffer.from(JSON.stringify({ user_id: "synthetic-manus", email: "sample@example.com" })).toString("base64url");
    const token = `eyJhbGciOiJIUzI1NiJ9.${claims}.NEVER_EXPORT_MANUS`;
    const manus = async (body, manual = false, status = 200) => fetchManusLimits(manual ? { manusSessionToken: `session_id=${token}; unrelated=NEVER_EXPORT_OTHER` } : {}, {
        env: {}, now: () => Date.parse(now),
        readManusDesktopSession: () => ({ token, userId: "synthetic-manus", email: "sample@example.com" }),
        fetch: async (url, init) => {
            assert.equal(url, "https://api.manus.im/user.v1.UserService/GetAvailableCredits");
            assert.equal(init.method, "POST");
            assert.equal(init.headers.Authorization, `Bearer ${token}`);
            assert.equal(init.headers.Cookie, undefined);
            assert.equal(init.redirect, "error");
            assert.equal(init.credentials, "omit");
            return { ok: status === 200, status, json: async () => body };
        },
    });
    const balance = { totalCredits: 1180, refreshCredits: 180, maxRefreshCredits: 300,
        refreshInterval: "daily", nextRefreshTime: "2026-12-01T00:00:00Z", proMonthlyCredits: 1200, periodicCredits: 900 };
    const local = await manus(balance);
    const web = await manus(balance, true);
    const row = save("manus-credits", [[local]])[0];
    assert.equal(row.provider, "manus");
    assert.equal(row.windows.find(w => w.kind === "daily").remainingPercent, 60);
    assert.equal(row.windows.find(w => w.label === "Monthly").remainingPercent, 75);
    assert.equal(row.windows.find(w => w.metric === "credits").remaining, 1180);
    assert.equal(row.windows.find(w => w.metric === "credits").remainingPercent, null);
    assert.equal(row.windows.find(w => w.metric === "credits").showMeter, false);
    assert.equal(save("manus-devices", [[local], [web]]).length, 1);
    for (const [name, body, status, expected] of [
        ["zero", { totalCredits: 0 }, 200, 0],
        ["negative", { totalCredits: -5 }, 200, -5],
        ["no-ceiling", { totalCredits: 250, refreshCredits: 20 }, 200, 250],
        ["missing", {}, 200, null],
        ["invalid", { totalCredits: "unknown" }, 200, null],
        ["expired", {}, 401, null],
    ]) {
        const value = save(`manus-${name}`, [[await manus(body, true, status)]])[0];
        if (expected === null) {
            assert.equal(value.status, name === "expired" ? "unauthorized" : "unavailable");
            assert.deepEqual(value.windows, []);
        } else {
            assert.equal(value.windows.length, 1);
            assert.equal(value.windows[0].remaining, expected);
            assert.equal(value.windows[0].remainingPercent, null);
            assert.equal(value.windows[0].currency, "CREDITS");
            assert.equal(value.windows[0].resetsAt, null);
            assert.equal(value.windows[0].showMeter, false);
        }
    }

    const { fetchMuseLimits } = require("./src/shared/providers/muse/limits.js");
    const cookie = "sample=NEVER_EXPORT_MUSE_COOKIE";
    const action = "a".repeat(40);
    const subscription = { tier: { name: "Maximum" }, usage: { percentUsed: 3, resetsAt: 1796083200 },
        usageRowValueLabel: "3% used (2.8B tokens left)", topupBalance: 60_000_000, topupTotal: 80_000_000,
        topupRowValueLabel: "25% used (1.5B tokens left)" };
    const muse = async (sub = subscription, status = 200, configured = true) => fetchMuseLimits(configured ? { museWebCookie: cookie } : {}, {
        now: () => Date.parse(now), platform: "linux", env: { PATH: "/synthetic/bin" },
        providerRuntimeState: new Map(),
        existsSync: path => path === "/synthetic/bin/muse",
        execFile: (command, args, options, callback) => {
            assert.equal(command, "/synthetic/bin/muse");
            assert.deepEqual(args, ["usage", "--json"]);
            assert.equal(options.env.MUSE_NO_AUTO_UPDATE, "1");
            const child = Object.assign(new EventEmitter(), { stdin: { end() {} }, kill() {} });
            queueMicrotask(() => callback(null, JSON.stringify({ schemaVersion: 1, allowance: {
                tier: "Muse Code High Usage", window: { usedPercent: 25 }, weekly: { usedPercent: 80 }
            } }), ""));
            return child;
        },
        museWebFetch: async (url, init) => {
            assert.equal(new URL(url).origin, "https://muse.ai");
            assert.equal(init.redirect, "manual");
            assert.equal(init.credentials, "omit");
            const path = new URL(url).pathname;
            if (init.method === "POST") {
                assert.equal(init.headers.Cookie, cookie);
                assert.equal(init.headers["Next-Action"], action);
                return new Response(`1:${JSON.stringify({ success: true, subscription: sub })}`, { status });
            }
            if (path === "/") {
                assert.equal(init.headers.Cookie, cookie);
                return new Response('<script src="/_next/static/chunks/runtime.js"></script>');
            }
            assert.equal(init.headers.Cookie, undefined, "Do not send the session when loading script chunks");
            if (path.endsWith("runtime.js")) return new Response('e.A(321).then(({loadSettingsData:e})=>e({}));[321,s=>{s.v(t=>Promise.all(["static/chunks/settings.js"].map(t=>s.l(t))))}');
            assert.equal(path, "/_next/static/chunks/settings.js");
            return new Response(`"${action}","fetchSubscriptionAction"`);
        },
    });
    const products = await muse();
    assert.deepEqual(products.map(p => p.source), ["cli", "web"]);
    const rows = save("muse-products", [products, products]);
    assert.equal(rows.length, 3, "Keep two CLI device rows and one cross-device web product");
    assert.equal(rows.filter(p => p.source === "cli").length, 2);
    const webRow = rows.find(p => p.source === "web");
    assert.equal(webRow.planLabel, "Maximum");
    assert.equal(webRow.windows[0].remainingPercent, 97);
    assert.equal(webRow.windows[0].resetDescription, "2.8B tokens left");
    assert.equal(webRow.windows[1].remaining, 60);
    assert.equal(webRow.windows[1].currency, "USD");
    assert.equal(webRow.windows[1].resetDescription, "1.5B tokens left");
    assert.equal(webRow.windows[1].showMeter, false);
    for (const [name, sub, status] of [
        ["zero", { ...subscription, usage: { ...subscription.usage, percentUsed: 100 }, topupBalance: 0, topupRowValueLabel: "100% used (0 tokens left)" }, 200],
        ["money-only", { ...subscription, topupRowValueLabel: "25% used" }, 200],
        ["missing", {}, 200],
        ["expired", {}, 403],
    ]) {
        const value = save(`muse-web-${name}`, [[(await muse(sub, status)).find(p => p.source === "web")]])[0];
        if (name === "missing" || name === "expired") {
            assert.equal(value.status, name === "expired" ? "unauthorized" : "unavailable");
            assert.deepEqual(value.windows, []);
        } else {
            assert.equal(value.windows[1].remaining, name === "zero" ? 0 : 60);
            assert.equal(value.windows[1].remainingPercent, null);
            if (name === "zero") assert.equal(value.windows[0].remainingPercent, 0);
        }
    }
    const absent = await muse(subscription, 200, false);
    assert.equal(absent.find(p => p.source === "web").removed, true);
    // Removed lanes are runtime invalidation markers, not provider rows sent to the Hub.
    assert.equal(save("muse-cli-only", [absent.filter(row => !row.removed)]).length, 1);
    const stale = save("muse-stale-web", [[{ ...products[1], updatedAt: new Date(Date.parse(now) - 1_800_000).toISOString() }], [products[0]]]);
    assert.equal(stale.length, 2, "A fresh CLI row must not hide the web product");

    // The collector correction stays on desktop. Check transport of its positive/empty counters.
    const counters = { timedTokens: 1400, timedOutputTokens: 120, timedDurationMs: 1200 };
    const usage = { ...device.periods.today, totalTokens: 1400, outputTokens: 120, throughputAvailable: true, ...counters,
        modelThroughput: { "synthetic-model": counters }, sessions: {
            positive: { client: "codex", sessionId: "positive", totalTokens: 1400, outputTokens: 120, ...counters },
            empty: { client: "codex", sessionId: "empty", totalTokens: 0, outputTokens: 0, timedOutputTokens: 0, timedDurationMs: 0 },
        } };
    const record = { ...device, deviceId: "synthetic-corrected-throughput", periods: { ...device.periods, today: usage } };
    const hub = createHub({ secret, dataFile: join(source, "corrected-throughput.json") });
    hub.ingest(record);
    const stats = hub.getStats();
    assert.equal(stats.periods.today.timedOutputTokens, 120);
    assert.equal(stats.periods.today.timedDurationMs, 1200);
    cases["corrected-throughput"] = { records: [record], providers: stats.limits.providers, throughput: counters };
    responses["corrected-throughput.json"] = stats;
    return { cases, responses };
}
