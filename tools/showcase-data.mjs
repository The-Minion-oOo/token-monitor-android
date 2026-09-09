const dayMs = 24 * 60 * 60 * 1000;

const costFor = (tokens) => Number((tokens / 1_000_000 * 0.48).toFixed(2));

const split = (total, weights) => {
    const entries = Object.entries(weights);
    let assigned = 0;
    return Object.fromEntries(entries.map(([name, weight], index) => {
        const value = index === entries.length - 1 ? total - assigned : Math.floor(total * weight);
        assigned += value;
        return [name, value];
    }));
};

const attribution = (tokens) => {
    const cacheReadTokens = Math.floor(tokens * 0.72);
    const cacheWriteTokens = Math.floor(tokens * 0.08);
    const outputTokens = Math.floor(tokens * 0.16);
    return {
        tokens,
        cost: costFor(tokens),
        cacheReadTokens,
        cacheWriteTokens,
        outputTokens,
        unclassifiedTokens: tokens - cacheReadTokens - cacheWriteTokens - outputTokens,
        tokenComponentsAvailable: true,
    };
};

const usagePeriod = (totalTokens, projects = {}) => {
    const clients = split(totalTokens, { codex: 0.71, claude: 0.22, opencode: 0.07 });
    const models = split(totalTokens, { "gpt-5.6-sol": 0.54, "gpt-5.6-terra": 0.17, "claude-sonnet-4-5": 0.22, "qwen3-coder": 0.07 });
    const componentMaps = (values, field) => Object.fromEntries(
        Object.entries(values).map(([name, tokens]) => [name, attribution(tokens)[field]]),
    );
    return {
        totalTokens,
        costUsd: costFor(totalTokens),
        clients,
        clientCosts: Object.fromEntries(Object.entries(clients).map(([name, tokens]) => [name, costFor(tokens)])),
        clientCacheReads: componentMaps(clients, "cacheReadTokens"),
        clientCacheWrites: componentMaps(clients, "cacheWriteTokens"),
        clientOutputs: componentMaps(clients, "outputTokens"),
        clientUnclassifiedTokens: componentMaps(clients, "unclassifiedTokens"),
        clientModels: {
            codex: { "gpt-5.6-sol": models["gpt-5.6-sol"], "gpt-5.6-terra": models["gpt-5.6-terra"] },
            claude: { "claude-sonnet-4-5": models["claude-sonnet-4-5"] },
            opencode: { "qwen3-coder": models["qwen3-coder"] },
        },
        clientModelCosts: {
            codex: { "gpt-5.6-sol": costFor(models["gpt-5.6-sol"]), "gpt-5.6-terra": costFor(models["gpt-5.6-terra"]) },
            claude: { "claude-sonnet-4-5": costFor(models["claude-sonnet-4-5"]) },
            opencode: { "qwen3-coder": costFor(models["qwen3-coder"]) },
        },
        models,
        modelCosts: Object.fromEntries(Object.entries(models).map(([name, tokens]) => [name, costFor(tokens)])),
        modelCacheReads: componentMaps(models, "cacheReadTokens"),
        modelCacheWrites: componentMaps(models, "cacheWriteTokens"),
        modelOutputs: componentMaps(models, "outputTokens"),
        modelUnclassifiedTokens: componentMaps(models, "unclassifiedTokens"),
        projects,
    };
};

const dailyHistory = (endDate) => {
    const end = new Date(`${endDate}T12:00:00.000Z`);
    return Array.from({ length: 65 }, (_, index) => {
        const date = new Date(end.getTime() - (64 - index) * dayMs);
        const wave = (index * 1_173_821) % 4_900_000;
        const spike = index % 11 === 4 ? 3_600_000 : index % 17 === 8 ? 2_100_000 : 0;
        const tokens = 1_850_000 + wave + spike;
        const perClientTokens = split(tokens, { codex: 0.71, claude: 0.22, opencode: 0.07 });
        const perModelTokens = split(tokens, { "gpt-5.6-sol": 0.54, "gpt-5.6-terra": 0.17, "claude-sonnet-4-5": 0.22, "qwen3-coder": 0.07 });
        const components = attribution(tokens);
        return {
            date: date.toISOString().slice(0, 10),
            tokens,
            cost: components.cost,
            messages: 12 + (index * 7) % 34,
            activeTimeMs: (45 + (index * 19) % 210) * 60_000,
            cacheReadTokens: components.cacheReadTokens,
            cacheWriteTokens: components.cacheWriteTokens,
            outputTokens: components.outputTokens,
            unclassifiedTokens: components.unclassifiedTokens,
            tokenComponentsAvailable: true,
            perClient: Object.fromEntries(Object.entries(perClientTokens).map(([name, value]) => [name, attribution(value)])),
            perModel: Object.fromEntries(Object.entries(perModelTokens).map(([name, value]) => [name, attribution(value)])),
        };
    });
};

// Reset times are relative to capture time so screenshots always show a countdown.
const resetIn = (hours) => new Date(Date.now() + hours * 3_600_000).toISOString();

export const createShowcaseResponses = (date = "2026-09-06") => {
    const daily = dailyHistory(date);
    const todayTokens = daily.at(-1).tokens;
    const monthTokens = daily.filter((entry) => entry.date.startsWith(date.slice(0, 7)))
        .reduce((sum, entry) => sum + entry.tokens, 0);
    const allTimeTokens = 1_842_650_900;
    const projectTokens = split(todayTokens, {
        "mobile-companion": 0.48,
        "research-workspace": 0.27,
        "automation-lab": 0.17,
        "docs-and-notes": 0.08,
    });
    const projects = Object.fromEntries(Object.entries(projectTokens).map(([id, tokens]) => [id, {
        projectId: `example/${id}`,
        projectLabel: id.split("-").map((part) => part[0].toUpperCase() + part.slice(1)).join(" "),
        tokens,
        costUsd: costFor(tokens),
        sessionCount: 1 + tokens % 4,
        clients: split(tokens, { codex: 0.76, claude: 0.24 }),
    }]));
    const today = usagePeriod(todayTokens, projects);
    today.sessions = {
        "codex:showcase-001": {
            sessionId: "showcase-001",
            client: "codex",
            projectLabel: "Mobile Companion",
            totalTokens: projectTokens["mobile-companion"],
            costUsd: costFor(projectTokens["mobile-companion"]),
            messageCount: 18,
            startedAt: `${date}T08:15:00.000Z`,
            lastUsedAt: `${date}T11:52:00.000Z`,
            models: { "gpt-5.6-sol": projectTokens["mobile-companion"] },
        },
        "claude:showcase-002": {
            sessionId: "showcase-002",
            client: "claude",
            projectLabel: "Research Workspace",
            totalTokens: projectTokens["research-workspace"],
            costUsd: costFor(projectTokens["research-workspace"]),
            messageCount: 11,
            startedAt: `${date}T09:30:00.000Z`,
            lastUsedAt: `${date}T11:20:00.000Z`,
            models: { "claude-sonnet-4-5": projectTokens["research-workspace"] },
        },
    };

    const month = usagePeriod(monthTokens);
    const allTime = usagePeriod(allTimeTokens);
    const primaryTokens = Math.floor(todayTokens * 0.74);
    const secondaryTokens = todayTokens - primaryTokens;
    const scaleHistoryAttribution = (values, weight) => Object.fromEntries(
        Object.entries(values).map(([name, value]) => [name, attribution(Math.floor(value.tokens * weight))]),
    );
    const scaleHistoryEntry = (entry, weight) => {
        const tokens = Math.floor(entry.tokens * weight);
        return {
            ...entry,
            ...attribution(tokens),
            perClient: scaleHistoryAttribution(entry.perClient, weight),
            perModel: scaleHistoryAttribution(entry.perModel, weight),
        };
    };
    const makeDevice = (id, hostname, platform, osName, osVersion, tokens, weight) => ({
        deviceId: id,
        hostname,
        platform,
        osName,
        osVersion,
        updatedAt: `${date}T12:00:00.000Z`,
        receivedAt: `${date}T12:00:01.000Z`,
        ageMs: 1000,
        stale: false,
        syncUploadIntervalMs: 15000,
        historyAvailable: true,
        trackedClients: ["codex", "claude", "opencode"],
        periods: {
            today: usagePeriod(tokens),
            month: usagePeriod(Math.floor(monthTokens * weight)),
            allTime: usagePeriod(Math.floor(allTimeTokens * weight)),
        },
        history: {
            daily: daily.map((entry) => scaleHistoryEntry(entry, weight)),
            monthly: [],
        },
    });
    const devices = [
        makeDevice("showcase-desktop", "Studio Workstation", "win32", "Windows", "11 24H2", primaryTokens, 0.74),
        makeDevice("showcase-laptop", "Travel Laptop", "darwin", "macOS", "15.6", secondaryTokens, 0.26),
    ];
    const monthly = [
        { month: "2026-04", tokens: 196_420_000, cost: 94.28 },
        { month: "2026-05", tokens: 244_810_000, cost: 117.51 },
        { month: "2026-06", tokens: 281_360_000, cost: 135.05 },
        { month: "2026-07", tokens: 318_900_000, cost: 153.07 },
        { month: "2026-08", tokens: 472_650_000, cost: 226.87 },
        { month: "2026-09", tokens: monthTokens, cost: costFor(monthTokens) },
    ];
    const history = { daily, monthly, summary: { totalTokens: allTimeTokens, totalCost: costFor(allTimeTokens) } };
    const stats = {
        updatedAt: `${date}T12:00:01.000Z`,
        periods: { today, month, allTime },
        devices,
        limits: {
            updatedAt: `${date}T11:59:00.000Z`,
            providers: [
                {
                    provider: "codex",
                    accountName: "Demo account",
                    accountEmail: "demo@sample.invalid",
                    plan: "Plus",
                    status: "ok",
                    sourceDeviceId: "showcase-desktop",
                    windows: [
                        { kind: "weekly", label: "Weekly", usedPercent: 42, remainingPercent: 58, resetsAt: resetIn(69), metric: "percent" },
                        { kind: "spark", label: "GPT-5.3 Codex Spark", usedPercent: 12, remainingPercent: 88, resetsAt: resetIn(4.97), metric: "percent" },
                    ],
                },
                {
                    provider: "claude",
                    accountName: "Demo account",
                    accountEmail: "demo@sample.invalid",
                    plan: "Pro",
                    status: "ok",
                    sourceDeviceId: "showcase-desktop",
                    windows: [
                        { kind: "session", label: "Session", usedPercent: 18, remainingPercent: 82, resetsAt: resetIn(1.98), metric: "percent" },
                        { kind: "weekly", label: "Weekly", usedPercent: 31, remainingPercent: 69, resetsAt: resetIn(88), metric: "percent" },
                    ],
                },
            ],
        },
        historyPreview: history,
        subscriptionsUpdatedAt: `${date}T11:59:00.000Z`,
        staleAfterMs: 300000,
    };
    return {
        health: {
            ok: true,
            role: "hub",
            runtime: "node-hub",
            hubBuild: { runtimeBuildId: "showcase-v0.54.0" },
            deviceCount: devices.length,
            secretRequired: true,
            now: `${date}T12:00:01.000Z`,
        },
        stats,
        devices: { devices },
        history,
        subscriptions: {
            ok: true,
            updatedAt: `${date}T11:59:00.000Z`,
            subscriptions: [
                { id: "showcase-codex", provider: "codex", planName: "Plus", amountMinor: 2000, currency: "USD", startDate: "2026-08-01", interval: "month", autoRenew: true },
                { id: "showcase-claude", provider: "claude", planName: "Pro", amountMinor: 2000, currency: "USD", startDate: "2026-08-15", interval: "month", autoRenew: true },
            ],
        },
    };
};
