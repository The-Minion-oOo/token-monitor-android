import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFile } from "node:fs/promises";
import { basename, join } from "node:path";

export const productionPackage = "io.github.theminionooo.tokenmonitor";
export const productionCertificate = "eed5a820371ac158c038e5a55243b2e4e7f10ffdf764963b2808d152d3821c2c";

export function parseProperties(text) {
    return Object.fromEntries(text.split(/\r?\n/).filter(line => line.trim() && !/^\s*[#!]/.test(line))
        .map(line => { const index = line.indexOf("="); return [line.slice(0, index).trim(), line.slice(index + 1).trim()]; }));
}

export function validateMetadata(release, properties, upstream) {
    assert.equal(release.schemaVersion, 1, "Unsupported release metadata schema");
    assert.equal(release.repository, "The-Minion-oOo/token-monitor-android");
    assert.equal(release.packageName, productionPackage, "Production package changed");
    assert.equal(release.signingCertificateSha256, productionCertificate, "Production signing identity changed");
    const candidate = release.candidate;
    assert.ok(["candidate", "published"].includes(candidate.status), "Unknown release status");
    assert.match(candidate.versionName, /^v\d+\.\d+\.\d+$/);
    assert.ok(Number.isSafeInteger(candidate.versionCode) && candidate.versionCode > 0);
    assert.equal(candidate.tag, `android-${candidate.versionName}-r${candidate.versionCode % 1000}`);
    assert.equal(candidate.versionCode, Number(properties.tokenMonitorVersionCode), "release.json and Gradle version codes differ");
    assert.equal(candidate.versionName, properties.tokenMonitorVersionName);
    assert.equal(candidate.versionName, `v${upstream.version}`);
    assert.equal(upstream.repository, "Javis603/token-monitor");
    assert.equal(properties.tokenMonitorUpstreamVersion, upstream.version);
    assert.equal(properties.tokenMonitorUpstreamTag, upstream.tag);
    assert.equal(properties.tokenMonitorUpstreamCommit, upstream.commit);
    assert.equal(upstream.tag, `v${upstream.version}`);
    assert.match(upstream.commit, /^[a-f0-9]{40}$/);
    assert.equal(upstream.protocolFixture, `app/src/test/resources/protocol/${upstream.tag}`);
    if (candidate.status === "candidate") {
        assert.ok(candidate.versionCode > release.published.versionCode, "Candidate must upgrade the published version");
    } else {
        for (const field of ["tag", "versionName", "versionCode"]) assert.equal(candidate[field], release.published[field], "Published build and publication record differ");
    }
    assert.equal(release.published.tag, `android-${release.published.versionName}-r${release.published.versionCode % 1000}`);
    assert.match(release.published.sourceCommit, /^[a-f0-9]{40}$/);
    assert.ok(Array.isArray(release.requiredChecks) && release.requiredChecks.length > 0);
    for (const check of release.requiredChecks) {
        assert.match(check.workflow, /^[a-z0-9-]+\.yml$/);
        assert.ok(Array.isArray(check.jobs) && check.jobs.length > 0 && check.jobs.every(job => typeof job === "string" && job.length > 0));
    }
    return candidate;
}

export async function loadMetadata(root) {
    const [release, properties, upstream] = await Promise.all([
        readFile(join(root, "release.json"), "utf8").then(JSON.parse),
        readFile(join(root, "gradle.properties"), "utf8").then(parseProperties),
        readFile(join(root, "upstream.json"), "utf8").then(JSON.parse),
    ]);
    validateMetadata(release, properties, upstream);
    // Keep the installer and release tooling on the same pinned identity.
    const installer = await readFile(join(root, "app/src/main/java/io/github/theminionooo/tokenmonitor/data/update/ReleaseUpdates.kt"), "utf8");
    assert.ok(installer.includes(`releaseCertificate = "${productionCertificate}"`), "Installer certificate differs from release identity");
    assert.ok(installer.includes(`packageName = "${productionPackage}"`), "Installer package differs from release identity");
    return { release, properties, upstream };
}

export function parseApkIdentity(badging, certificates) {
    const packageLine = badging.split(/\r?\n/).find(line => line.startsWith("package:")) || "";
    const fields = Object.fromEntries([...packageLine.matchAll(/(?:^|\s)(name|versionCode|versionName)='([^']*)'/g)].map(match => [match[1], match[2]]));
    const signers = [...certificates.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9:]+)\s*$/gm)];
    assert.equal(signers.length, 1, "APK must have exactly one signer");
    return { packageName: fields.name, versionCode: Number(fields.versionCode), versionName: fields.versionName,
        certificate: signers[0][1].replaceAll(":", "").toLowerCase() };
}

export function validateApkIdentity(identity, release) {
    assert.equal(identity.packageName, release.packageName, "Wrong APK package");
    assert.equal(identity.versionCode, release.candidate.versionCode, "Wrong APK version code");
    assert.equal(identity.versionName, release.candidate.versionName, "Wrong APK version name");
    assert.equal(identity.certificate, release.signingCertificateSha256, "APK signing certificate does not match published upgrades");
}

export function createUpdateManifest(release, bytes) {
    const { candidate } = release;
    return { schemaVersion: 1, packageName: release.packageName, tag: candidate.tag,
        versionName: candidate.versionName, versionCode: candidate.versionCode,
        apk: `token-monitor-${candidate.tag}.apk`, sizeBytes: bytes.length,
        sha256: createHash("sha256").update(bytes).digest("hex") };
}

export function validateBundle(release, bytes, manifest, checksum, apkPath) {
    const expected = createUpdateManifest(release, bytes);
    assert.deepEqual(manifest, expected, "Update manifest does not describe these APK bytes and release metadata");
    assert.equal(basename(apkPath), expected.apk, "Unexpected APK filename");
    assert.equal(checksum.trim(), `${expected.sha256}  ${expected.apk}`, "Checksum sidecar differs from APK");
    assert.ok(bytes.length > 0 && bytes.length <= 100 * 1024 * 1024, "APK outside installer size bounds");
}
