const marker = "<!-- token-monitor-upstream-review -->";

export function compareReleaseTags(left, right) {
    const parse = tag => {
        const match = /^v(\d+)\.(\d+)\.(\d+)$/.exec(tag);
        if (!match) throw new Error(`Unsupported stable upstream tag: ${tag}`);
        return match.slice(1).map(Number);
    };
    const a = parse(left), b = parse(right);
    for (let index = 0; index < 3; index++) if (a[index] !== b[index]) return Math.sign(a[index] - b[index]);
    return 0;
}

export function planIssueSync({ issues, currentTag, latestTag, releaseUrl }) {
    const newer = compareReleaseTags(latestTag, currentTag) > 0;
    const actions = [];
    for (const issue of issues) {
        const match = /^Review Token Monitor (v\d+\.\d+\.\d+)$/.exec(issue.title);
        const owned = issue.user?.login === "github-actions[bot]" &&
            (issue.body?.includes(marker) || /^The verified desktop baseline is v\d+\.\d+\.\d+\. Review https:\/\/github\.com\/Javis603\/token-monitor\/releases\/tag\//.test(issue.body || ""));
        if (!issue.pull_request && issue.state === "open" && match && owned && compareReleaseTags(match[1], currentTag) <= 0) {
            actions.push({ type: "close", number: issue.number, state_reason: "completed" });
        }
    }
    const title = `Review Token Monitor ${latestTag}`;
    // A manually closed review is not reopened or replaced by the weekly check.
    if (newer && !issues.some(issue => !issue.pull_request && issue.title === title)) {
        if (releaseUrl !== `https://github.com/Javis603/token-monitor/releases/tag/${latestTag}`) throw new Error("Unexpected upstream release URL");
        actions.push({ type: "create", title, body: `${marker}\nThe verified desktop baseline is ${currentTag}. Review ${releaseUrl} and follow docs/UPSTREAM_SYNC.md before changing compatibility metadata.` });
    }
    return actions;
}
