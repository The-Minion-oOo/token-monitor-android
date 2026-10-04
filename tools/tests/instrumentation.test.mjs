import assert from "node:assert/strict";
import { test } from "node:test";
import { verifyInstrumentationReport } from "../check-instrumentation.mjs";

const report = '<testsuites tests="2" failures="0" errors="0" skipped="0"><testsuite>' +
    '<testcase name="pairing" classname="ConnectionTest" />' +
    '<testcase name="expiry" classname="WidgetTest" /></testsuite></testsuites>';

test("the complete distinct instrumentation suite passes", () => {
    verifyInstrumentationReport(report, 2);
});

test("partial, missing and duplicate tests cannot satisfy the suite count", () => {
    assert.throws(() => verifyInstrumentationReport(report, 43), /test count differs/);
    assert.throws(() => verifyInstrumentationReport(report.replace(/<testcase[^>]+\/>/, ''), 2), /Test cases differ/);
    assert.throws(() => verifyInstrumentationReport(report.replace('name="expiry" classname="WidgetTest"', 'name="pairing" classname="ConnectionTest"'), 2), /Duplicate/);
    assert.throws(() => verifyInstrumentationReport('<testsuite />', 2), /Missing/);
});

test("failures, errors and skipped tests fail the verification", () => {
    for (const field of ["failures", "errors", "skipped"]) {
        assert.throws(() => verifyInstrumentationReport(report.replace(`${field}="0"`, `${field}="1"`), 2));
    }
});
