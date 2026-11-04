import assert from "node:assert/strict";
import { join } from "node:path";

/** Released collector, synthetic HTTP only. No browser cookie store or real account. */
export async function checkV086Limits(require, { createHub, source, secret, device, now }) {
    const { fetchCommandcodeLimits, COMMANDCODE_CREDITS_URL, COMMANDCODE_SUBSCRIPTIONS_URL } = require("./src/shared/providers/commandcode/limits.js");
    const cases = {}, responses = {};
    for (const [name, start, end, days] of [
        ["billing-28", "2026-02-01T08:00:00Z", "2026-03-01T08:00:00Z", 28],
        ["billing-29", "2028-02-01T08:00:00Z", "2028-03-01T08:00:00Z", 29],
        ["billing-30", "2026-04-19T08:00:00Z", "2026-05-19T08:00:00Z", 30],
        ["billing-31", "2026-10-19T08:00:00Z", "2026-11-19T08:00:00Z", 31],
        ["billing-missing", undefined, "2026-11-19T08:00:00Z", null],
        ["billing-invalid", "invalid", "2026-11-19T08:00:00Z", null],
        ["billing-reversed", "2026-12-19T08:00:00Z", "2026-11-19T08:00:00Z", null],
    ]) {
        const calls = [];
        const provider = await fetchCommandcodeLimits({ commandcodeCookie: "__Secure-commandcode_prod_.session_token=NEVER_EXPORT_COOKIE" }, {
            env: {}, now: () => Date.parse(now),
            fetch: async (url, init) => {
                calls.push(url);
                assert.ok([COMMANDCODE_CREDITS_URL, COMMANDCODE_SUBSCRIPTIONS_URL].includes(url));
                assert.ok(init.headers.Cookie.includes("NEVER_EXPORT_COOKIE"));
                return { ok: true, status: 200, json: async () => url === COMMANDCODE_CREDITS_URL
                    ? { credits: { monthlyCredits: 8.5, purchasedCredits: 0 }, windowLimits: {
                        fiveHour: { cap: 3, used: 1.5 }, weekly: { cap: 6, used: 1.5 } } }
                    : { success: true, data: { planId: "individual-go", status: "active", currentPeriodStart: start, currentPeriodEnd: end } } };
            },
        });
        assert.deepEqual(new Set(calls), new Set([COMMANDCODE_CREDITS_URL, COMMANDCODE_SUBSCRIPTIONS_URL]));
        assert.equal(provider.status, "ok");
        const records = [{ ...device, deviceId: `synthetic-${name}`, limits: { updatedAt: now, providers: [provider] } }];
        const hub = createHub({ secret, dataFile: join(source, `${name}.json`) });
        hub.ingest(records[0]);
        const stats = hub.getStats(), windows = stats.limits.providers[0].windows;
        const monthly = windows.find(window => window.label === "Monthly");
        assert.equal(monthly.windowMinutes, days === null ? null : days * 1440);
        assert.equal(monthly.remaining, 8.5);
        assert.equal(monthly.currency, "USD");
        assert.equal(monthly.resetsAt, new Date(end).toISOString());
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
        cases[name] = { records, providers: stats.limits.providers };
        responses[`${name}.json`] = stats;
    }
    return { cases, responses };
}
