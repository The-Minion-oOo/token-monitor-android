import assert from "node:assert/strict";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";

export async function checkV0931Projects(require, { createHub, source, secret, device, now }) {
    const { projectIdentity } = require("./src/shared/sessionMetadata.js");
    const { projectRollupFromSessions } = require("./src/shared/usage.js");

    // Real worktree metadata layout in the disposable desktop source export.
    const repository = join(source, "Example App");
    const worktree = join(source, "example-fix");
    const gitDir = join(repository, ".git", "worktrees", "example-fix");
    await mkdir(gitDir, { recursive: true });
    await mkdir(worktree);
    await writeFile(join(worktree, ".git"), "gitdir: ../Example App/.git/worktrees/example-fix\n");
    await writeFile(join(gitDir, "commondir"), "../..\n");

    const mainProject = projectIdentity(repository);
    const worktreeProject = projectIdentity(worktree);
    assert.deepEqual(worktreeProject, mainProject);
    assert.equal(mainProject.projectLabel, "Example App");
    const sessions = {
        "codex:main-demo": { client: "codex", sessionId: "main-demo", totalTokens: 60,
            lastUsedAt: now, ...mainProject },
        "claude:worktree-demo": { client: "claude", sessionId: "worktree-demo", totalTokens: 40,
            lastUsedAt: now, ...worktreeProject },
    };
    const period = { totalTokens: 100, clients: { codex: 60, claude: 40 }, sessions,
        projects: projectRollupFromSessions(sessions) };
    const record = { ...device, deviceId: "synthetic-worktree", trackedClients: ["codex", "claude"],
        periods: { today: period, month: period, allTime: period }, history: {} };
    const hub = createHub({ host: "127.0.0.1", port: 0, secret, dataFile: join(source, "v0931-projects-store.json") });
    hub.ingest(record);
    await hub.start();
    try {
        const response = await fetch(`http://127.0.0.1:${hub.server.address().port}/api/stats`, {
            headers: { Authorization: `Bearer ${secret}` }, signal: AbortSignal.timeout(5000), redirect: "error",
        });
        assert.equal(response.status, 200);
        const stats = await response.json();
        const projects = Object.values(stats.periods.today.projects);
        assert.equal(projects.length, 1);
        assert.equal(projects[0].label, "Example App");
        assert.equal(projects[0].tokens, 100);
        return { record, stats };
    } finally {
        await hub.stop();
    }
}
