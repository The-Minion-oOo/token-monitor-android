import assert from "node:assert/strict";
import { readdir, readFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

// AGP writes one JUnit report for the single emulator used by each CI job.
export function verifyInstrumentationReport(xml, expectedCount) {
    assert.ok(Number.isSafeInteger(expectedCount) && expectedCount > 0, "Expected test count must be positive");
    const summary = xml.match(/<testsuites\b([^>]*)>/)?.[1];
    assert.ok(summary, "Missing instrumentation suite summary");
    const totals = Object.fromEntries([...summary.matchAll(/\b(tests|failures|errors|skipped)="(\d+)"/g)]
        .map(([, name, value]) => [name, Number(value)]));
    assert.equal(totals.tests, expectedCount, "Instrumentation test count differs from the required suite");
    for (const field of ["failures", "errors", "skipped"]) assert.equal(totals[field], 0, `Instrumentation ${field}`);
    const cases = [...xml.matchAll(/<testcase\b([^>]*)>/g)].map(([, attributes]) => {
        const fields = Object.fromEntries([...attributes.matchAll(/\b(classname|name)="([^"]+)"/g)]
            .map(([, name, value]) => [name, value]));
        assert.ok(fields.classname && fields.name, "Test case identity is missing");
        return JSON.stringify([fields.classname, fields.name]);
    });
    assert.equal(cases.length, expectedCount, "Test cases differ from the suite summary");
    assert.equal(new Set(cases).size, expectedCount, "Duplicate instrumentation test cases");
}

async function main() {
    const [directory, count] = process.argv.slice(2);
    assert.ok(directory, "Provide the connected test report directory and expected test count");
    const reports = (await readdir(directory)).filter(name => name.startsWith("TEST-") && name.endsWith(".xml"));
    assert.equal(reports.length, 1, "Expected one emulator report");
    verifyInstrumentationReport(await readFile(join(directory, reports[0]), "utf8"), Number(count));
    console.log(`Verified ${count} distinct instrumentation tests with no failures, errors or skips.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
