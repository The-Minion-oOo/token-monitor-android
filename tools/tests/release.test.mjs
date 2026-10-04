import assert from "node:assert/strict";
import { test } from "node:test";
import { createUpdateManifest, parseApkIdentity, productionCertificate, productionPackage,
    validateApkIdentity, validateBundle, validateMetadata } from "../release-metadata.mjs";
import { requireSuccessfulJobs } from "../release-preflight.mjs";

const release = { schemaVersion: 1, repository: "The-Minion-oOo/token-monitor-android", packageName: productionPackage,
    signingCertificateSha256: productionCertificate,
    candidate: { status: "candidate", tag: "android-v0.66.0-r2", versionName: "v0.66.0", versionCode: 660002 },
    published: { tag: "android-v0.66.0-r1", versionName: "v0.66.0", versionCode: 660001, sourceCommit: "8".repeat(40) },
    requiredChecks: [{ workflow: "android.yml", jobs: ["build"] }] };
const upstream = { repository: "Javis603/token-monitor", version: "0.66.0", tag: "v0.66.0",
    commit: "e7bac3aff6a1bc7cf74fd149a26d2730cfe32de8", protocolFixture: "app/src/test/resources/protocol/v0.66.0" };
const properties = { tokenMonitorVersionCode: "660002", tokenMonitorVersionName: "v0.66.0",
    tokenMonitorUpstreamVersion: upstream.version, tokenMonitorUpstreamTag: upstream.tag, tokenMonitorUpstreamCommit: upstream.commit };

test("candidate metadata must match the build and reviewed upstream pin", () => {
    validateMetadata(release, properties, upstream);
    assert.throws(() => validateMetadata(release, { ...properties, tokenMonitorVersionCode: "660001" }, upstream), /version codes differ/);
    assert.throws(() => validateMetadata(release, properties, { ...upstream, commit: "a".repeat(40) }));
    const stale = structuredClone(release);
    stale.published.versionCode = stale.candidate.versionCode;
    assert.throws(() => validateMetadata(stale, properties, upstream), /must upgrade/);
});

test("published status can be recorded without inventing the next build", () => {
    const published = structuredClone(release);
    published.candidate.status = "published";
    published.published = { ...published.published, ...published.candidate };
    validateMetadata(published, properties, upstream);
    published.published.tag = "android-v0.66.0-r1";
    assert.throws(() => validateMetadata(published, properties, upstream), /publication record differ/);
});

test("wrong package, version or signing identity cannot become a release bundle", () => {
    const identity = { packageName: productionPackage, versionCode: 660002, versionName: "v0.66.0", certificate: productionCertificate };
    validateApkIdentity(identity, release);
    for (const change of [{ packageName: `${productionPackage}.preview` }, { versionCode: 660001 },
        { versionName: "v0.65.0" }, { certificate: "0".repeat(64) }]) {
        assert.throws(() => validateApkIdentity({ ...identity, ...change }, release));
    }
});

test("APK inspection requires exactly one certificate and reads the actual manifest", () => {
    const badging = `package: name='${productionPackage}' versionCode='660002' versionName='v0.66.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'`;
    const cert = `Signer #1 certificate SHA-256 digest: ${productionCertificate}\n`;
    validateApkIdentity(parseApkIdentity(badging, cert), release);
    assert.throws(() => parseApkIdentity(badging, ""), /exactly one signer/);
    assert.throws(() => parseApkIdentity(badging, cert + cert.replace("#1", "#2")), /exactly one signer/);
});

test("manifest, checksum and APK bytes must describe the same artifact", () => {
    const bytes = Buffer.from("synthetic APK bytes for integrity validation");
    const manifest = createUpdateManifest(release, bytes);
    const checksum = `${manifest.sha256}  ${manifest.apk}\n`;
    validateBundle(release, bytes, manifest, checksum, manifest.apk);
    assert.throws(() => validateBundle(release, Buffer.concat([bytes, Buffer.from("changed")]), manifest, checksum, manifest.apk));
    assert.throws(() => validateBundle(release, bytes, { ...manifest, versionCode: 660003 }, checksum, manifest.apk));
    assert.throws(() => validateBundle(release, bytes, manifest, `deadbeef  ${manifest.apk}`, manifest.apk));
    assert.throws(() => validateBundle(release, bytes, manifest, checksum, "preview.apk"));
});

const sha = "a".repeat(40);
const check = { workflow: "emulator.yml", jobs: ["interaction-api36", "optimized-api26"] };
const run = { id: 1, head_sha: sha, head_branch: "main", event: "push", run_number: 10,
    run_attempt: 1, status: "completed", conclusion: "success" };
const jobs = check.jobs.map(name => ({ name, head_sha: sha, status: "completed", conclusion: "success" }));

test("release preflight only accepts successful required jobs on the exact main push", () => {
    requireSuccessfulJobs(check, sha, [run], new Map([[1, jobs]]));
    for (const changed of [{ head_sha: "b".repeat(40) }, { event: "pull_request" }, { head_branch: "feature" }]) {
        assert.throws(() => requireSuccessfulJobs(check, sha, [{ ...run, ...changed }], new Map([[1, jobs]])), /No main push run/);
    }
    assert.throws(() => requireSuccessfulJobs(check, sha, [run], new Map([[1, [jobs[0]]]])), /Missing or ambiguous/);
    assert.throws(() => requireSuccessfulJobs(check, sha, [run], new Map([[1, [jobs[0], { ...jobs[1], conclusion: "skipped" }]]])), /did not pass/);
    assert.throws(() => requireSuccessfulJobs(check, sha, [run], new Map([[1, [jobs[0], { ...jobs[1], head_sha: "b".repeat(40) }]]])), /different commit/);
});

test("an older green run cannot hide a newer failed or incomplete run", () => {
    const newer = { ...run, id: 2, run_number: 11, conclusion: "failure" };
    assert.throws(() => requireSuccessfulJobs(check, sha, [run, newer], new Map([[1, jobs], [2, jobs]])), /did not pass/);
    assert.throws(() => requireSuccessfulJobs(check, sha, [run, { ...newer, status: "in_progress", conclusion: null }], new Map()), /not complete/);
    assert.throws(() => requireSuccessfulJobs(check, sha, [{ ...run, run_attempt: 2, conclusion: "failure" }, run], new Map([[1, jobs]])), /did not pass/);
});
