import assert from "node:assert/strict";
import { EventEmitter } from "node:events";
import { join } from "node:path";

/** Actual released collectors with synthetic processes/HTTP only; never run a CLI or use credentials. */
export async function checkV0841Limits(require, { createHub, source, secret, device, now }) {
    const cases = {}, responses = {};
    const save = (name, provider) => {
        const records = [{ ...device, deviceId: `synthetic-${name}`, limits: { updatedAt: now, providers: [provider] } }];
        const hub = createHub({ secret, dataFile: join(source, `${name}.json`) });
        hub.ingest(records[0]);
        const stats = hub.getStats();
        assert.ok(!JSON.stringify(stats).includes("NEVER_EXPORT"), `${name} exported credentials`);
        cases[name] = { records, providers: stats.limits.providers };
        responses[`${name}.json`] = stats;
        return stats.limits.providers[0];
    };
    const { fetchKiroLimits } = require("./src/shared/providers/kiro/limits.js");
    const binary = join("/synthetic/bin", "kiro-cli");
    const pinned = ["chat", "--no-interactive", "--agent-engine=v2", "/usage"];
    const oldFlag = ["chat", "--no-interactive", "/usage"];
    const legacy = ["chat", "--no-interactive", "--agent-engine=v1", "--legacy-ui", "/usage"];
    const report = used => `Estimated Usage | resets on 2026-12-01 | KIRO STUDENT\nCredits (${used} of 1000 covered in plan), 1.6%`;
    const kiro = async (name, steps) => {
        const calls = [];
        const value = await fetchKiroLimits({}, {
            now: () => Date.parse(now), platform: "linux", env: { PATH: "/synthetic/bin", TOKEN_MONITOR_TEST: "NEVER_EXPORT_KIRO" },
            existsSync: path => path === binary,
            spawn: (command, args, options) => {
                calls.push({ command, args: [...args], options });
                const step = steps[calls.length - 1];
                assert.ok(step, "Unexpected extra Kiro probe");
                const child = Object.assign(new EventEmitter(), { stdout: new EventEmitter(), stderr: new EventEmitter(), kill() {} });
                queueMicrotask(() => {
                    child.stdout.emit("data", step.stdout || "");
                    child.stderr.emit("data", step.stderr || "");
                    child.emit("close", step.code || 0);
                });
                return child;
            },
        });
        // Check outside the collector's catch so an assertion cannot masquerade as provider unavailability.
        assert.equal(calls.length, steps.length, "Unexpected or missing Kiro probe");
        for (const [index, call] of calls.entries()) {
            assert.equal(call.command, binary);
            assert.deepEqual(call.args, steps[index].args);
            assert.deepEqual(call.options.stdio, ["ignore", "pipe", "pipe"]);
            assert.equal(call.options.windowsHide, true);
            assert.equal(call.options.env.TERM, "xterm-256color");
        }
        return save(name, value);
    };
    for (const [name, steps] of [
        ["kiro-v2", [{ args: pinned, stdout: report(16.27) }]],
        ["kiro-legacy-flag", [{ args: pinned, stdout: "wrapper banner", stderr: "error: unexpected argument '--agent-engine' found", code: 2 }, { args: oldFlag, stdout: report(16.27) }]],
        ["kiro-summary-fallback", [{ args: pinned, stdout: "Plan: KIRO POWER | 1 usage breakdowns" }, { args: legacy, stdout: report(16.27) }]],
    ]) {
        const value = await kiro(name, steps);
        assert.equal(value.status, "ok");
        assert.equal(value.source, "cli");
        assert.equal(value.windows[0].used, 16.27);
        assert.equal(value.windows[0].limit, 1000);
        assert.ok(Math.abs(value.windows[0].remainingPercent - 98.373) < 0.000001);
    }
    assert.equal((await kiro("kiro-zero", [{ args: pinned, stdout: report(1000) }])).windows[0].remainingPercent, 0);
    for (const [name, stdout, expected] of [
        ["kiro-unreadable", "Could not retrieve usage information", "unavailable"],
        ["kiro-signed-out", "not logged in", "notConfigured"],
    ]) {
        const value = await kiro(name, [{ args: pinned, stdout }]);
        assert.equal(value.status, expected);
        assert.equal(value.windows.length, 0);
    }

    const { fetchCopilotLimits } = require("./src/shared/providers/copilot/limits.js");
    for (const mixed of [false, true]) {
        const value = save(mixed ? "copilot-mixed" : "copilot-no-reset", await fetchCopilotLimits({ copilotApiToken: "NEVER_EXPORT_COPILOT_TOKEN" }, {
            now: () => Date.parse(now), env: {},
            fetch: async (url, init) => {
                assert.equal(init.headers.Authorization, "token NEVER_EXPORT_COPILOT_TOKEN");
                assert.ok(["https://api.github.com/user", "https://api.github.com/copilot_internal/user"].includes(url));
                return { ok: true, status: 200, json: async () => url.endsWith("/user") && !url.includes("copilot_internal")
                    ? { login: "synthetic-copilot", id: 100 }
                    : { copilot_plan: "individual", quota_reset_date: "2026-12-01", quota_snapshots: {
                        chat: { unlimited: true, entitlement: 0, remaining: 0, percent_remaining: 100 },
                        ...(mixed ? { premium_interactions: { unlimited: false, entitlement: 300, remaining: 225, percent_remaining: 75 } } : {}),
                    } } };
            },
        }));
        assert.equal(value.status, "ok");
        const chat = value.windows.find(window => window.label === "Chat");
        assert.equal(chat.resetsAt, null);
        assert.equal(chat.resetDescription, "");
        assert.equal(chat.remainingPercent, 100);
        if (mixed) assert.equal(value.windows.find(window => window.label === "Premium").resetsAt, "2026-12-01T00:00:00.000Z");
    }
    return { cases, responses };
}
