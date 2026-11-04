import assert from "node:assert/strict";

/** Execute the released status summarizer without fetching any live endpoint. */
export function checkV086Status(require) {
    const { SERVICE_STATUS_PROVIDERS, summarizeAtomFeedProvider } = require("./src/electron/serviceStatus.js");
    const provider = SERVICE_STATUS_PROVIDERS.find(value => value.id === "deepseek");
    assert.equal(provider.summaryUrl, "https://status.deepseek.com/feed.atom");
    const entry = (title, status, day = "04", components = "API, Web") => `<entry><title>${title}</title><updated>2026-11-${day}T10:00:00Z</updated><summary type="html">&lt;p&gt;&lt;strong&gt;Status:&lt;/strong&gt; ${status}&lt;/p&gt;&lt;p&gt;&lt;strong&gt;Affected components:&lt;/strong&gt; ${components}&lt;/p&gt;</summary></entry>`;
    const feed = (...entries) => `<feed xmlns="http://www.w3.org/2005/Atom">${entries.join("")}</feed>`;
    const cases = [
        ["outage", feed(entry("API unavailable", "monitoring"), entry("Old outage", "resolved", "03"))],
        ["resolved", feed(entry("Old incident", "resolved"))],
        ["maintenance", feed(entry("Scheduled maintenance", "monitoring"))],
        ["degraded", feed(entry("Slow responses", "investigating"), entry("Scheduled maintenance", "monitoring"))],
        ["unknown-status", feed(entry("Incident", "???"))],
        ["chinese", feed(entry("API 部分中断", "monitoring"))],
        ["sorted", feed(entry("Old", "investigating", "03"), entry("API &amp; Web", "monitoring"))],
        ["empty", feed()],
        ["invalid", "<html>not a feed</html>"],
        ["truncated", feed(entry("Incident", "resolved")).slice(0, -4)],
    ].map(([name, body]) => {
        const value = summarizeAtomFeedProvider(provider, body, { checkedAt: "2026-11-04T12:00:00Z" });
        return { name, body, health: value.status, description: value.incidentTitle || value.description,
            affectedComponents: value.componentIssues.length, incidentCount: value.incidentCount, maintenanceCount: value.maintenanceCount };
    });
    assert.equal(cases.find(row => row.name === "outage").health, "outage");
    assert.equal(cases.find(row => row.name === "unknown-status").health, "degraded");
    assert.equal(cases.find(row => row.name === "empty").health, "unknown");
    return cases;
}
