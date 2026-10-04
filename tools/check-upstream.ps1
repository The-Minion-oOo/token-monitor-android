[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$metadataPath = Join-Path $PSScriptRoot '..\upstream.json'
$metadata = Get-Content -Raw -LiteralPath $metadataPath | ConvertFrom-Json
$headers = @{ 'User-Agent' = 'token-monitor-android-upstream-check' }
if ($env:GH_TOKEN) { $headers.Authorization = "Bearer $env:GH_TOKEN" }
$latest = Invoke-RestMethod -Headers $headers -Uri "https://api.github.com/repos/$($metadata.repository)/releases/latest"
if ([string]$latest.tag_name -notmatch '^v\d+\.\d+\.\d+$' -or [string]$metadata.tag -notmatch '^v\d+\.\d+\.\d+$') {
    throw 'Only stable semantic release tags can be compared automatically.'
}
$available = [version]([string]$latest.tag_name).TrimStart('v') -gt [version]([string]$metadata.tag).TrimStart('v')

Write-Output "Verified baseline: $($metadata.tag)"
Write-Output "Latest upstream:  $($latest.tag_name)"

# Report source drift even between releases. A changed file is a review signal,
# not proof of a wire-format break; compatibility is still established by fixtures.
$repository = Invoke-RestMethod -Headers $headers -Uri "https://api.github.com/repos/$($metadata.repository)"
$comparison = Invoke-RestMethod -Headers $headers -Uri "https://api.github.com/repos/$($metadata.repository)/compare/$($metadata.commit)...$($repository.default_branch)"
$relevant = @($comparison.files | Where-Object {
    $_.filename -match '^src/(hub/|shared/(usage|syncPayload|history|clientCatalog|clientTracking|limitProviders)\.js)' -or
    $_.filename -match '^worker/src/index\.js$' -or $_.filename -match '^assets/icons/'
})
$report = @(
    '## Desktop compatibility review',
    "Verified release: $($metadata.tag). Latest release: $($latest.tag_name).",
    "Upstream development is $($comparison.ahead_by) commits ahead of the verified baseline.",
    '',
    'Changes below need protocol or presentation review before updating Android compatibility. Unreleased changes do not change the verified version.'
)
foreach ($file in $relevant) { $report += "- $($file.filename) ($($file.status))" }
if ($relevant.Count -eq 0) { $report += 'No watched protocol, provider catalog, or icon files changed.' }
$report += "Comparison: $($comparison.html_url)"
$report | Write-Output
if ($env:GITHUB_STEP_SUMMARY) { $report | Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY }

if ($env:GITHUB_OUTPUT) {
    Add-Content -LiteralPath $env:GITHUB_OUTPUT -Value "update_available=$($available.ToString().ToLowerInvariant())"
    Add-Content -LiteralPath $env:GITHUB_OUTPUT -Value "current_tag=$($metadata.tag)"
    Add-Content -LiteralPath $env:GITHUB_OUTPUT -Value "latest_tag=$($latest.tag_name)"
    Add-Content -LiteralPath $env:GITHUB_OUTPUT -Value "release_url=$($latest.html_url)"
}

if ($available) {
    Write-Output "A newer Token Monitor release needs compatibility review."
} else {
    Write-Output "Android compatibility metadata is current."
}
