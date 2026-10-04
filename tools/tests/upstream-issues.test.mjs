import assert from "node:assert/strict";
import { test } from "node:test";
import { compareReleaseTags, planIssueSync } from "../upstream-issues.mjs";

const issue = (tag, number = 1) => ({ number, title: `Review Token Monitor ${tag}`, state: "open",
    body: `The verified desktop baseline is v0.63.0. Review https://github.com/Javis603/token-monitor/releases/tag/${tag} and follow docs/UPSTREAM_SYNC.md before changing compatibility metadata.`,
    user: { login: "github-actions[bot]" } });
const context = { currentTag: "v0.66.0", latestTag: "v0.66.0", releaseUrl: "https://github.com/Javis603/token-monitor/releases/tag/v0.66.0" };

test("already verified bot reminders close even when no newer release exists", () => {
    assert.deepEqual(planIssueSync({ ...context, issues: [issue("v0.63.1"), issue("v0.66.0", 2)] }), [
        { type: "close", number: 1, state_reason: "completed" }, { type: "close", number: 2, state_reason: "completed" },
    ]);
});

test("manual issues, unrelated bot issues, pull requests and future reminders stay untouched", () => {
    const unrelated = issue("v0.63.1");
    assert.deepEqual(planIssueSync({ ...context, issues: [
        { ...unrelated, user: { login: "maintainer" } }, { ...unrelated, body: "Please investigate a user-reported defect" },
        { ...unrelated, pull_request: { url: "https://example.invalid/pr" } }, issue("v0.67.0"),
    ] }), []);
});

test("new reviews are created once and closed decisions are respected", () => {
    const next = { ...context, latestTag: "v0.67.0", releaseUrl: "https://github.com/Javis603/token-monitor/releases/tag/v0.67.0" };
    const actions = planIssueSync({ ...next, issues: [] });
    assert.equal(actions.length, 1);
    assert.equal(actions[0].title, "Review Token Monitor v0.67.0");
    assert.match(actions[0].body, /token-monitor-upstream-review/);
    assert.deepEqual(planIssueSync({ ...next, issues: [{ ...issue("v0.67.0"), state: "closed" }] }), []);
    assert.throws(() => planIssueSync({ ...next, issues: [], releaseUrl: "https://example.invalid" }), /Unexpected upstream/);
});

test("upstream comparison orders numeric versions and refuses prerelease tags", () => {
    assert.equal(compareReleaseTags("v0.66.0", "v0.9.0"), 1);
    assert.equal(compareReleaseTags("v0.63.1", "v0.64.0"), -1);
    assert.equal(compareReleaseTags("v0.66.0", "v0.66.0"), 0);
    assert.throws(() => compareReleaseTags("v0.67.0-beta", "v0.66.0"));
    assert.deepEqual(planIssueSync({ ...context, currentTag: "v0.67.0", issues: [] }), []);
});
