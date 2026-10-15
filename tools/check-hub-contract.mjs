import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import http from "node:http";
import { createRequire } from "node:module";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const script = fileURLToPath(import.meta.url);
const root = resolve(dirname(script), "..");
const args = process.argv.slice(2);
const option = name => { const i = args.indexOf(name); return i < 0 ? undefined : args[i + 1]; };

async function isolatedContract(source, output, upstream) {
    const require = createRequire(join(source, "package.json"));
    assert.equal(require("./package.json").version, upstream.version, "Pinned desktop source has another version");
    const { createHub } = require("./src/hub/server.js");
    const now = new Date().toISOString();
    const day = now.slice(0, 10);
    const period = { totalTokens: 1234, costUsd: 0.42, unpricedTokens: 321, clients: { mcode: 1234 },
        clientUnpricedTokens: { mcode: 321 }, modelUnpricedTokens: { "minimax-m2.5": 321 },
        clientModelUnpricedTokens: { mcode: { "minimax-m2.5": 321 } },
        projects: { synthetic: { projectId: "synthetic", label: "Synthetic project", totalTokens: 1234, costUsd: 0.42, unpricedTokens: 321 } },
        sessions: { "mcode:contract-session": { sessionId: "contract-session", client: "mcode",
            title: "Synthetic shared title", totalTokens: 1234, unpricedTokens: 321, costUsd: 0.42, prompt: "NEVER_EXPORT_PROMPT", messages: ["NEVER_EXPORT_MESSAGES"] } },
        clientCosts: { mcode: 0.42 }, models: { "minimax-m2.5": 1234 },
        modelCosts: { "minimax-m2.5": 0.42 }, clientModels: { mcode: { "minimax-m2.5": 1234 } } };
    const device = { deviceId: "contract-desktop", hostname: "Synthetic desktop", platform: "linux",
        agentVersion: upstream.version, updatedAt: now, trackedClients: ["mcode"],
        periods: { today: period, month: period, allTime: period }, historyAvailable: true,
        history: { daily: [{ date: day, tokens: 1234, cost: 0.42, unpricedTokens: 321,
            perClient: { mcode: { tokens: 1234, cost: 0.42, unpricedTokens: 321 } },
            perModel: { "minimax-m2.5": { tokens: 1234, cost: 0.42, unpricedTokens: 321 } } }], monthly: [] } };
    const secret = "isolated-contract-secret";
    const hub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, "synthetic-data.json"), broadcastDelayMs: 5,
        logger: { error: error => { throw error; } } });
    // This setup populates only the temporary test Hub. Every HTTP request below is a GET.
    hub.ingest(device);
    let stream;
    const frames = [], pending = [];
    const nextFrame = () => frames.length ? Promise.resolve(frames.shift()) : new Promise((resolveFrame, reject) => {
        const entry = { resolve: frame => { clearTimeout(timer); resolveFrame(frame); } };
        const timer = setTimeout(() => { pending.splice(pending.indexOf(entry), 1); reject(new Error("Timed out waiting for Hub stream event")); }, 5000);
        pending.push(entry);
    });
    await hub.start();
    try {
        assert.equal(hub.bindHost, "127.0.0.1");
        const base = `http://127.0.0.1:${hub.server.address().port}`;
        const get = async (path, authenticated = true) => fetch(`${base}${path}`, {
            headers: authenticated ? { Authorization: `Bearer ${secret}` } : {},
            redirect: "error", signal: AbortSignal.timeout(5000),
        });
        assert.equal((await get("/api/stats", false)).status, 401, "Hub unexpectedly serves statistics without authentication");
        const responses = {};
        for (const name of ["health", "stats", "devices", "history", "subscriptions"]) {
            const response = await get(`/api/${name}`, name !== "health");
            assert.equal(response.status, 200, `${name} GET failed`);
            responses[`${name}.json`] = await response.json();
        }
        assert.equal(responses["health.json"].role, "hub");
        assert.equal(responses["health.json"].secretRequired, true);
        assert.equal(responses["stats.json"].periods.today.totalTokens, 1234);
        assert.equal(responses["stats.json"].periods.today.clients.mcode, 1234);
        assert.equal(responses["stats.json"].periods.today.unpricedTokens, 321);
        assert.equal(responses["stats.json"].periods.today.clientModelUnpricedTokens.mcode["minimax-m2.5"], 321);
        assert.ok(!JSON.stringify(responses["stats.json"]).includes("Synthetic shared title"), "Titles must be absent by default");
        assert.ok(!JSON.stringify(responses["stats.json"]).includes("NEVER_EXPORT"), "Conversation content must never cross the Hub");
        assert.ok(responses["stats.json"].syncSettingsRevisions, "Shared settings revisions must remain additive");
        assert.equal(responses["devices.json"].devices[0].deviceId, device.deviceId);
        assert.ok(responses["history.json"].daily.some(point => point.tokens === 1234));
        assert.ok(Array.isArray(responses["subscriptions.json"].subscriptions));
        stream = http.get(`${base}/api/stats/stream`, { headers: { Authorization: `Bearer ${secret}`, "x-token-monitor-stream": "2" } }, response => {
            assert.equal(response.statusCode, 200);
            let buffered = "";
            response.setEncoding("utf8");
            response.on("data", chunk => {
                buffered += chunk;
                while (buffered.includes("\n\n")) {
                    const boundary = buffered.indexOf("\n\n");
                    const block = buffered.slice(0, boundary);
                    buffered = buffered.slice(boundary + 2);
                    const event = /^event: (.+)$/m.exec(block)?.[1];
                    const data = /^data: (.+)$/m.exec(block)?.[1];
                    if (!event || !data) continue;
                    const frame = { event, data: JSON.parse(data) };
                    if (pending.length) pending.shift().resolve(frame); else frames.push(frame);
                }
            });
        });
        stream.on("error", () => {}); // The bounded frame wait supplies the failure message.
        const snapshot = await nextFrame();
        assert.equal(snapshot.event, "snapshot");
        assert.equal(snapshot.data.stats.periods.today.totalTokens, 1234);
        responses["stream-snapshot.json"] = snapshot.data;
        // A metadata-only ingest must produce v2 freshness, retaining the existing counters.
        hub.ingest({ ...device, updatedAt: new Date().toISOString() });
        const freshness = await nextFrame();
        assert.equal(freshness.event, "freshness");
        assert.equal(freshness.data.stats.periods, undefined);
        responses["freshness.json"] = freshness.data;
        hub.ingest({ ...device, periods: { ...device.periods, today: { ...period, totalTokens: 1235,
            clients: { mcode: 1235 }, models: { "minimax-m2.5": 1235 }, clientModels: { mcode: { "minimax-m2.5": 1235 } } } } });
        const changed = await nextFrame();
        assert.equal(changed.event, "stats");
        assert.equal(changed.data.stats.periods.today.totalTokens, 1235);
        responses["stream-stats.json"] = changed.data;
        // Consent setup is confined to another disposable Hub; Android still makes no writes.
        const titleHub = createHub({ port: 0, host: "127.0.0.1", secret, syncSessionTitles: true,
            dataFile: join(source, "synthetic-titles.json"), broadcastDelayMs: 5 });
        const policy = titleHub.setSyncTitlePolicy(device.deviceId, true);
        titleHub.ingest({ ...device, sessionTitleSyncGeneration: policy.generation });
        responses["titles-enabled.json"] = titleHub.getStats();
        assert.ok(JSON.stringify(responses["titles-enabled.json"]).includes("Synthetic shared title"));
        assert.ok(!JSON.stringify(responses["titles-enabled.json"]).includes("NEVER_EXPORT"));
        titleHub.setSyncTitlePolicy(device.deviceId, false);
        responses["titles-revoked.json"] = titleHub.getStats();
        assert.ok(!JSON.stringify(responses["titles-revoked.json"]).includes("Synthetic shared title"), "Revocation must clear stored titles");
        const dotsHub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, "synthetic-dots.json") });
        const dotsPeriod = { totalTokens: 400, costUsd: 0, unpricedTokens: 400, clients: { codex: 400 },
            clientUnpricedTokens: { codex: 400 }, models: { "unpriced-model": 400 },
            modelUnpricedTokens: { "unpriced-model": 400 },
            sessions: { dots: { sessionId: "dots", client: "codex", totalTokens: 400, unpricedTokens: 400,
                usageSource: "codex-dots-local", usageCoverage: "observed-only", codexLocalSessionKeys: ["NEVER_EXPORT_LOCAL_KEY"] } } };
        dotsHub.ingest({ ...device, periods: { today: dotsPeriod } });
        responses["dots.json"] = dotsHub.getStats();
        assert.equal(Object.values(responses["dots.json"].periods.today.sessions)[0].usageCoverage, "observed-only");
        assert.ok(!JSON.stringify(responses["dots.json"]).includes("NEVER_EXPORT_LOCAL_KEY"));
        // Relationships and activity go to another disposable Hub so the single-session checks above stay exact.
        const relationHub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, "synthetic-relations.json") });
        const parentNative = "0B8F6C2E-3D41-4A6B-9C0E-5A1D2F7E8B01"; // Upper case on purpose: the Hub lower-cases it.
        const row = (client, sessionId, model, tokens, extra = {}) => ({ sessionId, client, totalTokens: tokens, costUsd: 0.01,
            models: { [model]: tokens }, projectLabel: "Example Project", lastUsedAt: now, ...extra });
        const relationRows = [
            row("codex", "sample-codex-parent", "gpt-5", 600, { nativeSessionId: parentNative, waitingForInput: false }),
            row("codex", "sample-codex-subagent", "gpt-5", 150, { parentSessionId: "sample-codex-parent", parentClient: "codex",
                nativeSessionId: "5E7A91D4-2C3B-4F08-8A6D-1B9C0E4F7A22" }),
            row("codex", "sample-codex-continuation", "gpt-5", 250, { nativeSessionId: parentNative }),
            row("codex", "sample-codex-waiting", "gpt-5", 120, { waitingForInput: true, question: "NEVER_EXPORT_QUESTION" }),
            row("claude", "sample-t3-claude", "claude-sonnet-4-5", 300, { t3ThreadId: "sample-t3-thread-1", waitingForInput: true,
                liveActivity: { state: "running", observedAt: now, prompt: "NEVER_EXPORT_ACTIVITY" } }),
            row("codex", "sample-t3-codex", "gpt-5", 100, { t3ThreadId: "sample-t3-thread-1", parentSessionId: "sample-t3-claude", parentClient: "claude" }),
            row("claude", "sample-claude-live", "claude-sonnet-4-5", 90, { liveActivity: { state: "waiting", observedAt: now } }),
            row("cursor", "sand-subagent-sample-1", "claude-sonnet-4-5", 70, { liveActivity: { state: "running", observedAt: now } }),
            row("cursor", "sample-cursor-bot", "grok-bot-1", 50),
        ];
        const tally = pick => relationRows.reduce((result, entry) => {
            for (const [name, tokens] of Object.entries(pick(entry))) result[name] = (result[name] || 0) + tokens;
            return result;
        }, {});
        const relationPeriod = { totalTokens: relationRows.reduce((total, entry) => total + entry.totalTokens, 0),
            costUsd: relationRows.length * 0.01, clients: tally(entry => ({ [entry.client]: entry.totalTokens })),
            models: tally(entry => entry.models), sessions: Object.fromEntries(relationRows.map(entry => [`${entry.client}:${entry.sessionId}`, entry])) };
        relationHub.ingest({ ...device, trackedClients: ["codex", "claude", "cursor"], periods: { today: relationPeriod, month: relationPeriod } });
        responses["relations.json"] = relationHub.getStats();
        const related = responses["relations.json"].periods.today.sessions;
        // Upstream src/shared/usage.js: applyNativeSessionId, applyT3ThreadId, applyParentLink, normalizeSession and mergeSession.
        assert.equal(related["codex:sample-codex-parent"].nativeSessionId, parentNative.toLowerCase());
        assert.equal(related["codex:sample-codex-continuation"].nativeSessionId, parentNative.toLowerCase());
        assert.equal(related["codex:sample-codex-parent"].waitingForInput, false);
        assert.equal(related["codex:sample-codex-waiting"].waitingForInput, true);
        assert.equal(related["codex:sample-codex-subagent"].parentSessionId, "sample-codex-parent");
        assert.equal(related["codex:sample-codex-subagent"].parentClient, undefined, "A parent in the child's own client needs no parentClient");
        assert.equal(related["codex:sample-t3-codex"].parentSessionId, "sample-t3-claude");
        assert.equal(related["codex:sample-t3-codex"].parentClient, "claude");
        assert.equal(related["codex:sample-t3-codex"].t3ThreadId, "sample-t3-thread-1");
        assert.equal(related["claude:sample-t3-claude"].t3ThreadId, "sample-t3-thread-1");
        assert.deepEqual(related["claude:sample-t3-claude"].liveActivity, { state: "running", observedAt: now }, "Only state and time cross the Hub");
        assert.deepEqual(related["claude:sample-claude-live"].liveActivity, { state: "waiting", observedAt: now });
        assert.equal(related["claude:sample-t3-claude"].waitingForInput, undefined, "Question waiting belongs to Codex");
        assert.equal(related["cursor:sand-subagent-sample-1"].liveActivity, undefined, "Live activity is limited to Claude and Codex");
        assert.equal(related["cursor:sample-cursor-bot"].models["grok-bot-1"], 50);
        assert.ok(!JSON.stringify(responses["relations.json"]).includes("NEVER_EXPORT"), "Question and activity content must never cross the Hub");
        // v0.70.0: the CatPaw client and MiniMax pay-as-you-go balances. The Hub keeps one MiniMax account at a time (it
        // collapses limit providers by name), so each balance goes to its own disposable Hub. The balance windows come
        // from the released MiniMax code fed a stubbed balance response, so no request leaves this process.
        const { fetchMinimaxLimits } = require("./src/shared/providers/minimax/limits.js");
        const minimaxBalance = (region, keyName, amount) => fetchMinimaxLimits(
            { minimaxApiKey: `NEVER_EXPORT_MINIMAX_KEY_${keyName}`, minimaxBillingMode: "api", minimaxApiRegion: region },
            { env: {}, now: () => Date.parse(now), fetchJson: async () => ({ available_amount: amount, cash_balance: 999, base_resp: { status_code: 0, status_msg: "success" } }) });
        const catpawPeriod = { totalTokens: 1200, costUsd: 0, unpricedTokens: 1200, clients: { catpaw: 1200 }, clientCosts: { catpaw: 0 },
            models: { auto: 1200 }, modelCosts: { auto: 0 }, clientModels: { catpaw: { auto: 1200 } },
            clientUnpricedTokens: { catpaw: 1200 }, modelUnpricedTokens: { auto: 1200 }, clientModelUnpricedTokens: { catpaw: { auto: 1200 } },
            sessions: { "catpaw:sample-catpaw": { sessionId: "sample-catpaw", client: "catpaw", totalTokens: 1200, costUsd: 0, unpricedTokens: 1200,
                models: { auto: 1200 }, lastUsedAt: now } } };
        // Upstream src/shared/usage.js (normalizeClientName) and src/shared/providers/minimax/limits.js (parseMinimaxBalance).
        for (const [name, region, amount, currency] of [["balance", "cn", 12.5, "CNY"], ["balance-zero", "en", 0, "USD"], ["balance-negative", "en", -3.2, "USD"]]) {
            const balanceHub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, `synthetic-${name}.json`) });
            balanceHub.ingest({ ...device, trackedClients: ["catpaw"], periods: { today: catpawPeriod, month: catpawPeriod },
                limits: { updatedAt: now, providers: [await minimaxBalance(region, `${name}-key`, amount)] } });
            responses[`${name}.json`] = balanceHub.getStats();
            const provider = responses[`${name}.json`].limits.providers.find(entry => entry.provider === "minimax");
            assert.ok(provider, `A ${amount} balance must survive the Hub, zero and negative included`);
            assert.equal(provider.accountLabel, "Pay-as-you-go");
            assert.equal(provider.status, "ok");
            assert.equal(provider.windows.length, 1);
            assert.deepEqual({ kind: provider.windows[0].kind, metric: provider.windows[0].metric, label: provider.windows[0].label,
                remaining: provider.windows[0].remaining, currency: provider.windows[0].currency },
            { kind: "billing", metric: "credits", label: "Balance", remaining: amount, currency });
            assert.equal(provider.windows[0].usedPercent, null, "A balance carries no percentage");
            assert.equal(provider.windows[0].remainingPercent, null);
            assert.equal(provider.windows[0].resetsAt, null, "A balance has no reset time");
            assert.ok(!JSON.stringify(responses[`${name}.json`]).includes("NEVER_EXPORT"), "A MiniMax key must never cross the Hub");
        }
        const catpaw = responses["balance.json"].periods.today;
        assert.equal(catpaw.clients.catpaw, 1200, "catpaw must survive client normalization");
        assert.equal(catpaw.sessions["catpaw:sample-catpaw"].client, "catpaw");
        assert.equal(catpaw.clientUnpricedTokens.catpaw, 1200);
        responses["contract.json"] = { upstream, generatedAt: new Date().toISOString(), synthetic: true,
            expectedTodayTokens: 1234, expectedUpdatedTokens: 1235 };
        // Notion's collector exports only a hashed user/workspace identity and normalized quotas.
        const { fetchNotionLimits } = require("./src/shared/providers/notion/limits.js");
        const userId = "11111111-1111-4111-8111-111111111111";
        const workspaceIds = ["22222222-2222-4222-8222-222222222222", "33333333-3333-4333-8333-333333333333"];
        const notionAccounts = await Promise.all(workspaceIds.map((workspaceId, index) => fetchNotionLimits(
            { notionCookie: "NEVER_EXPORT_NOTION_COOKIE", notionWorkspaceId: workspaceId },
            { env: {}, now: () => Date.parse(now), fetch: async url => ({ status: 200, json: async () =>
                url.endsWith("getSpaces") ? { [userId]: { notion_user: { [userId]: { value: { id: userId } } },
                    space: { [workspaceId]: { value: { id: workspaceId, name: "NEVER_EXPORT_WORKSPACE_NAME",
                        subscription_tier: index ? "enterprise" : "business" } } } } } :
                    { status: "within_limit", resetsInSeconds: 7200,
                        window: { creditType: "basic_ai_credits", scope: "per_user", window: "6h", used: 25, limit: 100 },
                        billingPeriodWindow: { creditType: "basic_ai_credits", scope: "per_user", cadence: "billing_period",
                            used: index ? 10 : 40, limit: 100, periodEndMs: Date.parse(now) + 86400000 } } }) } )));
        const goosePeriod = { totalTokens: 1500, costUsd: 0.05, clients: { goose: 1500 }, clientCosts: { goose: 0.05 },
            models: { "claude-sonnet-4-5": 1500 }, modelCosts: { "claude-sonnet-4-5": 0.05 },
            clientModels: { goose: { "claude-sonnet-4-5": 1500 } },
            sessions: { "goose:sample-goose": { sessionId: "sample-goose", client: "goose", totalTokens: 1500,
                costUsd: 0.05, models: { "claude-sonnet-4-5": 1500 }, lastUsedAt: now } } };
        const workbuddyAccounts = [
            { provider: "workbuddy", accountKey: `sha256:${"a".repeat(64)}`, accountName: "Sample China account",
                source: "oauth", realm: "cn", status: "ok", updatedAt: now,
                windows: [{ kind: "billing", label: "Credits", metric: "credits", currency: "CREDITS", used: 300,
                    remaining: 700, limit: 1000, usedPercent: 30, remainingPercent: 70 }] },
            { provider: "workbuddy", accountKey: `sha256:${"b".repeat(64)}`, accountName: "Sample international account",
                source: "oauth", realm: "intl", status: "unauthorized", updatedAt: now, windows: [] },
        ];
        const allowanceHub = createHub({ port: 0, host: "127.0.0.1", secret, dataFile: join(source, "synthetic-allowances.json") });
        allowanceHub.ingest({ ...device, trackedClients: ["goose"], periods: { today: goosePeriod },
            limits: { updatedAt: now, providers: [...notionAccounts, ...workbuddyAccounts] } });
        responses["allowances.json"] = allowanceHub.getStats();
        const allowances = responses["allowances.json"].limits.providers;
        const notion = allowances.filter(entry => entry.provider === "notion");
        assert.equal(notion.length, 2, "Distinct selected Notion workspaces must remain separate");
        assert.notEqual(notion[0].accountKey, notion[1].accountKey);
        for (const account of notion) {
            assert.equal(account.status, "ok");
            assert.deepEqual(account.windows.map(window => [window.kind, window.label]), [["session", "Rolling"], ["billing", "Monthly"]]);
            assert.equal(account.windows[0].windowMinutes, 360);
        }
        assert.deepEqual(allowances.filter(entry => entry.provider === "workbuddy").map(entry => entry.status).sort(), ["ok", "unauthorized"]);
        assert.equal(responses["allowances.json"].periods.today.clients.goose, 1500);
        const serializedAllowances = JSON.stringify(responses["allowances.json"]);
        for (const sentinel of [userId, ...workspaceIds, "NEVER_EXPORT"]) assert.ok(!serializedAllowances.includes(sentinel));
        await mkdir(output, { recursive: true });
        for (const [name, value] of Object.entries(responses)) await writeFile(join(output, name), `${JSON.stringify(value, null, 2)}\n`);
        console.log(`Actual ${upstream.tag} Hub passed authenticated reads, snapshot, freshness and changed-stat checks. Synthetic responses: ${output}`);
    } finally {
        stream?.destroy();
        hub.server.closeAllConnections();
        await hub.stop();
    }
}

const workerSource = option("--isolated-source");
if (workerSource) {
    const upstream = JSON.parse(await readFile(join(workerSource, "contract-upstream.json"), "utf8"));
    await isolatedContract(workerSource, resolve(option("--output")), upstream);
} else {
    const upstream = JSON.parse(await readFile(join(root, "upstream.json"), "utf8"));
    const approved = option("--approved-commit");
    assert.equal(approved, upstream.commit, "Explicit --approved-commit must match the reviewed released upstream commit");
    assert.match(approved, /^[a-f0-9]{40}$/);
    const repository = resolve(option("--upstream-repo") || "");
    assert.ok(option("--upstream-repo"), "Provide the read-only desktop source repository");
    assert.equal(execFileSync("git", ["-C", repository, "rev-parse", `${approved}^{commit}`], { encoding: "utf8" }).trim(), approved);
    const directory = await mkdtemp(join(tmpdir(), "token-monitor-hub-contract-"));
    try {
        // Export committed Hub/shared files only; never use the working tree, collectors, data, or .env.
        const archive = execFileSync("git", ["-C", repository, "archive", "--format=tar", approved, "package.json", "src/hub", "src/shared"], { maxBuffer: 32 * 1024 * 1024 });
        execFileSync("tar", ["-xf", "-", "-C", directory], { input: archive });
        await writeFile(join(directory, "contract-upstream.json"), JSON.stringify(upstream));
        const env = Object.fromEntries(["PATH", "Path", "SystemRoot", "SYSTEMROOT", "WINDIR", "TEMP", "TMP"]
            .filter(key => process.env[key]).map(key => [key, process.env[key]]));
        Object.assign(env, { HOME: directory, USERPROFILE: directory, APPDATA: directory, LOCALAPPDATA: directory,
            XDG_CONFIG_HOME: directory, TOKEN_MONITOR_SHARED_DIR: directory, TZ: "UTC" });
        execFileSync(process.execPath, [script, "--isolated-source", directory, "--output", resolve(option("--output") || join(root, "app/build/hub-contract"))],
            { env, cwd: directory, stdio: "inherit", timeout: 30_000 });
    } finally {
        // mkdtemp created this exact directory under the platform temporary directory.
        assert.equal(dirname(directory), resolve(tmpdir()));
        assert.ok(directory.startsWith(join(resolve(tmpdir()), "token-monitor-hub-contract-")));
        await rm(directory, { recursive: true, force: true });
    }
}
