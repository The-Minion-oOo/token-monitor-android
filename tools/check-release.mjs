import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { appendFile, mkdir, readFile, readdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseArgs } from "node:util";
import { createUpdateManifest, loadMetadata, parseApkIdentity, validateApkIdentity, validateBundle } from "./release-metadata.mjs";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const { values } = parseArgs({ options: { apk: { type: "string" }, prepare: { type: "string" } } });
const { release } = await loadMetadata(root);
const sourceApk = values.apk;
if (sourceApk) {
    const sdk = process.env.ANDROID_SDK_ROOT || process.env.ANDROID_HOME;
    assert.ok(sdk, "ANDROID_SDK_ROOT or ANDROID_HOME is required to inspect the APK");
    const versions = (await readdir(join(sdk, "build-tools"))).filter(version => /^\d+\.\d+\.\d+$/.test(version))
        .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
    assert.ok(versions.length, "No stable Android build-tools found");
    const buildTools = join(sdk, "build-tools", versions.at(-1));
    const apk = resolve(sourceApk);
    const badging = execFileSync(join(buildTools, process.platform === "win32" ? "aapt.exe" : "aapt"), ["dump", "badging", apk], { encoding: "utf8" });
    // Invoke the signing library directly on Windows, avoiding cmd.exe quoting of paths.
    const certificates = process.platform === "win32"
        ? execFileSync(process.env.JAVA_HOME ? join(process.env.JAVA_HOME, "bin/java.exe") : "java", ["-jar", join(buildTools, "lib/apksigner.jar"), "verify", "--print-certs", apk], { encoding: "utf8" })
        : execFileSync(join(buildTools, "apksigner"), ["verify", "--print-certs", apk], { encoding: "utf8" });
    validateApkIdentity(parseApkIdentity(badging, certificates), release);
    const bytes = await readFile(apk);
    const output = values.prepare;
    if (output) {
        assert.equal(release.candidate.status, "candidate", "This build is already recorded as published");
        const manifest = createUpdateManifest(release, bytes);
        const outputDirectory = resolve(output);
        await mkdir(outputDirectory, { recursive: true });
        await writeFile(join(outputDirectory, manifest.apk), bytes);
        await writeFile(join(outputDirectory, `${manifest.apk}.sha256`), `${manifest.sha256}  ${manifest.apk}\n`);
        await writeFile(join(outputDirectory, "token-monitor-android-update.json"), `${JSON.stringify(manifest, null, 2)}\n`);
        validateBundle(release, await readFile(join(outputDirectory, manifest.apk)),
            JSON.parse(await readFile(join(outputDirectory, "token-monitor-android-update.json"), "utf8")),
            await readFile(join(outputDirectory, `${manifest.apk}.sha256`), "utf8"), manifest.apk);
        const notes = `docs/releases/${release.candidate.tag}.md`;
        await readFile(join(root, notes));
        if (process.env.GITHUB_OUTPUT) await appendFile(process.env.GITHUB_OUTPUT,
            `tag=${manifest.tag}\napk=${join(outputDirectory, manifest.apk)}\nmanifest=${join(outputDirectory, "token-monitor-android-update.json")}\nnotes=${notes}\ntitle=Android ${manifest.versionName} r${manifest.versionCode % 1000}\n`);
    } else {
        const directory = dirname(apk);
        validateBundle(release, bytes, JSON.parse(await readFile(join(directory, "token-monitor-android-update.json"), "utf8")),
            await readFile(`${apk}.sha256`, "utf8"), apk);
    }
    console.log(`Verified APK identity, signature, and release bundle for ${release.candidate.tag}.`);
} else {
    assert.ok(!values.prepare, "--prepare requires --apk");
    console.log(`Release metadata agrees with Gradle, upstream, and installer identity: ${release.candidate.tag}.`);
}
