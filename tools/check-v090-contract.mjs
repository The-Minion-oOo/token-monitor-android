import assert from "node:assert/strict";
import { join } from "node:path";

export async function checkV090Data(require, { createHub, source, secret, now, device }) {
    const { fetchHarkLimits } = require("./src/shared/providers/hark/limits.js");
    const { fetchQoderLimits } = require("./src/shared/providers/qoder/limits.js");
    const date = days => new Date(Date.parse(now) + days * 86400000).toISOString();
    const hark = await fetchHarkLimits({ harkCookie: "a".repeat(32) + "." + "b".repeat(24) }, {
        env: {},
        fetch: async () => Response.json({
            plan: { id: "free", name: "Hark Pro", unlimited: false },
            meters: { harkTokens: {
                dailyUsed: 80000, dailyLimit: 100000, dailyResetsAt: date(1),
                poolUsed: 500000, poolLimit: 1000000, poolResetsAt: date(30),
            } },
            autoTopup: { enabled: true, spentThisPeriodMinor: 125 },
        }),
    });
    const quota = (used, limit) => ({
        quota_summary: { used_value: used, limit_value: limit, remaining_value: limit - used, unit: "credits" },
    });
    const readQoder = teamLimit => fetchQoderLimits({ qoderCookie: "session=NEVER_EXPORT" }, {
        env: {},
        fetch: async url => {
            switch (new URL(url).pathname) {
                case "/api/v2/me/usages/big_model_credits":
                    return Response.json({ total_quota: quota(25, 100), next_reset_at: date(7) });
                case "/api/v1/me/organization-shared-usages/big_model_credits":
                    return Response.json({ shared_quota: quota(teamLimit / 5, teamLimit) });
                case "/api/v1/me/userplan":
                    return Response.json({ plan_tier: "PLAN_TIER_TEAM", next_refresh_date: date(14) });
                default:
                    throw new Error(`Unexpected quota endpoint: ${url}`);
            }
        },
    });
    const qoder = await readQoder(50);
    const personalOnly = await readQoder(0);
    assert.equal(hark.status, "ok");
    assert.deepEqual(hark.windows.map(window => window.used), [80000, 500000, 1.25]);
    assert.deepEqual(qoder.windows.map(window => [window.label, window.resetsAt]),
        [["Credits", date(7)], ["Team credits", date(14)]]);
    assert.deepEqual(personalOnly.windows.map(window => window.label), ["Credits"]);

    const today = { date: now.slice(0, 10), tokens: 10 };
    const earlier = { date: date(-500).slice(0, 10), tokens: 10 };
    const period = { totalTokens: 10, clients: { codex: 10 }, models: { "inkling-test": 10 } };
    const completeHistory = dailyTotals => ({
        daily: [today], monthly: [], dailyTotals, summary: { activeDaysComplete: true },
    });
    const makeRecord = (id, history, providers) => ({
        ...device, deviceId: `synthetic-v090-${id}`, trackedClients: ["codex"],
        periods: { today: period, month: period, allTime: period }, history,
        limits: { providers },
    });
    const cases = {}, responses = {};
    for (const complete of [true, false]) {
        const name = complete ? "v090-complete" : "v090-mixed";
        const records = [
            makeRecord("first", completeHistory([earlier, today]), [hark, complete ? qoder : personalOnly]),
            makeRecord("second", complete ? completeHistory([today]) : { daily: [today], monthly: [] }, []),
        ];
        const hub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, `${name}-store.json`) });
        records.forEach(record => hub.ingest(record));
        await hub.start();
        try {
            const read = async endpoint => {
                const response = await fetch(`http://127.0.0.1:${hub.server.address().port}/api/${endpoint}`, {
                    headers: { Authorization: `Bearer ${secret}` }, signal: AbortSignal.timeout(5000), redirect: "error",
                });
                assert.equal(response.status, 200);
                return response.json();
            };
            const stats = await read("stats");
            const history = await read("history");
            const historySummary = { activeDays: complete ? 2 : 1, activeDaysComplete: complete };
            for (const [key, value] of Object.entries(historySummary)) {
                assert.equal(stats.historyPreview.summary[key], value);
                assert.equal(history.summary[key], value);
            }
            assert.equal(history.dailyTotals?.length, complete ? 2 : undefined);
            assert.deepEqual(stats.limits.providers.map(row => row.provider).sort(), ["hark", "qoder"]);
            assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"));
            cases[name] = { records, providers: stats.limits.providers, historySummary };
            responses[`${name}.json`] = stats;
            responses[`${name}-history.json`] = history;
        } finally {
            await hub.stop();
        }
    }
    return { cases, responses };
}
