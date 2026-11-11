import assert from "node:assert/strict";

export async function checkV091Reads(request, version) {
    const currencies = ["USD", "AUD", "BRL", "CAD", "CHF", "CNY", "EUR", "GBP", "HKD", "INR", "JPY", "KRW", "SGD", "TWD"];
    const subscriptions = currencies.map(currency => ({
        id: `synthetic-${currency.toLowerCase()}`, provider: "openai", planName: `Synthetic ${currency} plan`,
        currency, amountMinor: ["JPY", "KRW"].includes(currency) ? 1234500 : 1234, startDate: "2026-01-01",
    }));
    subscriptions.push({ id: "synthetic-krw-topup", provider: "openai", kind: "topup", currency: "KRW",
        startDate: "2026-01-01", topUps: [{ id: "synthetic-payment", date: "2026-01-02", amountMinor: 456700 }] });
    const write = (path, method, body, headers = {}) => request(path, true, {
        method, headers: { "content-type": "application/json", ...headers }, body,
    });
    const written = await write("subscriptions", "PUT", JSON.stringify({ subscriptions, baseUpdatedAt: "" }));
    assert.equal(written.status, 200);
    await written.text();
    const response = await request("subscriptions");
    assert.equal(response.status, 200);
    const result = await response.json();
    assert.equal(result.subscriptions.length, subscriptions.length);
    const byId = new Map(result.subscriptions.map(row => [row.id, row]));
    for (const row of subscriptions) {
        const actual = byId.get(row.id);
        assert.equal(actual.currency, row.currency);
        if (row.kind === "topup") assert.equal(actual.topUps[0].amountMinor, row.topUps[0].amountMinor);
        else assert.equal(actual.amountMinor, row.amountMinor);
    }

    // Whitespace expands the synthetic producer upload without adding unrelated data to the read fixture.
    const ingest = size => write("ingest", "POST", JSON.stringify({
        deviceId: "synthetic-large-upload", hostname: "Synthetic desktop", agentVersion: version,
        updatedAt: new Date().toISOString(), today: { totalTokens: 321 },
    }).padEnd(size), { "x-token-monitor-response": "minimal" });
    const accepted = await ingest(1536 * 1024);
    assert.equal(accepted.status, 200);
    await accepted.text();
    const oversized = await ingest(2 * 1024 * 1024 + 1);
    assert.equal(oversized.status, 413);
    await oversized.text();
    const stats = await request("stats");
    assert.equal(stats.status, 200);
    assert.ok((await stats.json()).devices.some(device => device.deviceId === "synthetic-large-upload"));
    return { "v091-subscriptions.json": result };
}
