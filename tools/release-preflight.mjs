import assert from "node:assert/strict";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { loadMetadata } from "./release-metadata.mjs";

export function requireSuccessfulJobs(check, sha, runs, jobsByRun) {
    const matching = runs.filter(run => run.head_sha === sha && run.head_branch === "main" && run.event === "push")
        .sort((a, b) => b.run_number - a.run_number || b.run_attempt - a.run_attempt);
    const latest = matching[0];
    assert.ok(latest, `No main push run for ${check.workflow} at ${sha}`);
    assert.equal(latest.status, "completed", `${check.workflow} is not complete`);
    assert.equal(latest.conclusion, "success", `${check.workflow} did not pass`);
    const jobs = jobsByRun.get(latest.id) || [];
    for (const name of check.jobs) {
        const matchingJobs = jobs.filter(job => job.name === name);
        assert.equal(matchingJobs.length, 1, `Missing or ambiguous required job: ${name}`);
        assert.equal(matchingJobs[0].head_sha, sha, `${name} ran against a different commit`);
        assert.equal(matchingJobs[0].status, "completed", `${name} is incomplete`);
        assert.equal(matchingJobs[0].conclusion, "success", `${name} did not pass`);
    }
    return latest;
}

export async function githubGet(path, token = process.env.GH_TOKEN) {
    assert.ok(token, "GH_TOKEN is required for the read-only release preflight");
    const response = await fetch(`https://api.github.com/${path}`, { headers: {
        Accept: "application/vnd.github+json", Authorization: `Bearer ${token}`,
        "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "token-monitor-release-preflight",
    }, signal: AbortSignal.timeout(20_000), redirect: "error" });
    assert.ok(response.ok, `GitHub preflight request failed (${response.status})`);
    return response.json();
}

async function main() {
    const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
    const { release } = await loadMetadata(root);
    assert.equal(release.candidate.status, "candidate", "A new candidate is required to prepare a release");
    const sha = process.argv[2] || process.env.GITHUB_SHA;
    assert.match(sha || "", /^[a-f0-9]{40}$/, "Provide the complete release commit SHA");
    const base = `repos/${release.repository}`;
    const mainRef = await githubGet(`${base}/git/ref/heads/main`);
    assert.equal(mainRef.object.sha, sha, "Release commit is no longer current main");
    const published = await githubGet(`${base}/releases/latest`);
    assert.equal(published.tag_name, release.published.tag, "Published release changed; reconcile release.json before preparing another release");
    assert.equal(published.draft, false);
    assert.equal(published.prerelease, false);
    for (const check of release.requiredChecks) {
        const result = await githubGet(`${base}/actions/workflows/${check.workflow}/runs?head_sha=${sha}&event=push&branch=main&per_page=100`);
        const runs = result.workflow_runs || [];
        const latest = runs.filter(run => run.head_sha === sha && run.head_branch === "main" && run.event === "push")
            .sort((a, b) => b.run_number - a.run_number || b.run_attempt - a.run_attempt)[0];
        assert.ok(latest, `No ${check.workflow} run exists at ${sha}`);
        const jobs = [];
        for (let page = 1; ; page++) {
            const response = await githubGet(`${base}/actions/runs/${latest.id}/attempts/${latest.run_attempt}/jobs?per_page=100&page=${page}`);
            jobs.push(...response.jobs);
            if (jobs.length >= response.total_count) break;
        }
        requireSuccessfulJobs(check, sha, runs, new Map([[latest.id, jobs]]));
        console.log(`${check.workflow}: required jobs passed on ${sha}.`);
    }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
