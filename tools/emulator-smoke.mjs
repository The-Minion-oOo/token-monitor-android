import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { setTimeout } from "node:timers/promises";

const packageName = "io.github.theminionooo.tokenmonitor";
const serial = process.env.ANDROID_SERIAL || "emulator-5554";
assert.match(serial, /^emulator-\d+$/, "Smoke checks may only target a disposable emulator");
const apk = process.argv[2];
assert.ok(apk, "Provide the optimized APK path");
const adb = (...args) => execFileSync("adb", ["-s", serial, ...args], { encoding: "utf8", timeout: 30_000 });
assert.equal(adb("shell", "getprop", "ro.kernel.qemu").trim(), "1", "Refusing to use a physical device");
const output = resolve("build/emulator-smoke");
await mkdir(output, { recursive: true });
let logSince;
const runtimeLogs = () => adb("shell", "logcat", "-b", "main", "-b", "crash", "-d", "-T", logSince, "-s", "AndroidRuntime:E");
try {
    adb("install", "-r", apk);
    // API 26 can reject log clearing immediately after install. Read this run's logs instead.
    logSince = `${adb("shell", "date", "+%s").trim()}.000`;
    assert.match(logSince, /^\d+\.000$/);
    const launch = adb("shell", "am", "start", "-W", "-n", `${packageName}/.MainActivity`);
    await writeFile(`${output}/launch.txt`, launch);
    assert.match(launch, /Status: ok/);
    await setTimeout(3000);
    assert.match(adb("shell", "pidof", packageName).trim(), /^\d+( \d+)*$/, "Optimized app exited after launch");
    adb("shell", "input", "keyevent", "KEYCODE_HOME");
    assert.match(adb("shell", "am", "start", "-W", "-n", `${packageName}/.MainActivity`), /Status: ok/);
    await setTimeout(1000);
    assert.ok(adb("shell", "pidof", packageName).trim(), "Optimized app exited after resume");
    const logs = runtimeLogs();
    await writeFile(`${output}/android-runtime.txt`, logs);
    assert.ok(!logs.includes(`Process: ${packageName}`), "Optimized app crashed; see android-runtime.txt");
    console.log("Optimized API 26 APK installed, opened, and resumed without an app crash.");
} finally {
    try { if (logSince) await writeFile(`${output}/final-logcat.txt`, runtimeLogs()); } catch {}
    try { adb("shell", "am", "force-stop", packageName); } catch {}
}
