param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if (!$adb) { $adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe' }
    if (!(Test-Path $adb)) { throw 'Install Android platform-tools or put adb on PATH.' }
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -PtokenMonitorPreview=true --console=plain
    if ($LASTEXITCODE) { throw 'Preview build failed.' }
    foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
        & $adb -s $Device install -r $apk
        if ($LASTEXITCODE) { throw "Installation failed: $apk" }
    }
    $result = & $adb -s $Device shell am instrument -w -e class 'io.github.theminionooo.tokenmonitor.LaunchShowcaseTest,io.github.theminionooo.tokenmonitor.WidgetThemeTest#allShapesUseAppPaletteIncludingLightAndCustomThemes,io.github.theminionooo.tokenmonitor.WidgetDeckDesignTest' io.github.theminionooo.tokenmonitor.preview.test/androidx.test.runner.AndroidJUnitRunner
    $result | Write-Output
    if (($result -join "`n") -notmatch 'OK \(16 tests\)') { throw 'Showcase tests did not pass.' }
    $names = @('home','models','devices','projects','trends','settings','filtered-models','widget-compact','widget-portrait','widget-wide','widget-overview','widget-large','widget-picker-preview')
    $captures = @($names | ForEach-Object {
        @{ source = "launch-$_.png"; target = if ($_ -eq 'widget-picker-preview') { 'app/src/main/res/drawable-xxhdpi/widget_picker_preview.png' } else { "docs/images/$_.png" } }
    })
    $captures += @{ source = 'launch-widget-picker-preview.png'; target = 'docs/images/widget-picker-preview.png' }
    foreach ($theme in @('default','obsidian','porcelain','custom')) {
        $captures += @{ source = "theme-$theme-large-live.png"; target = "docs/images/widget-theme-$theme.png" }
    }
    foreach ($page in @('overview','limits','breakdown','activity')) {
        $captures += @{ source = "widget-deck-$page-default-360x220.png"; target = "docs/images/widget-pages-$page.png" }
    }
    foreach ($capture in $captures) {
        $start = [System.Diagnostics.ProcessStartInfo]::new()
        $start.FileName = $adb
        $start.Arguments = "-s $Device exec-out run-as io.github.theminionooo.tokenmonitor.preview cat files/$($capture.source)"
        $start.UseShellExecute = $false
        $start.RedirectStandardOutput = $true
        $start.CreateNoWindow = $true
        $process = [System.Diagnostics.Process]::Start($start)
        $target = $capture.target
        $file = [System.IO.File]::Create((Join-Path $root $target))
        try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
        $process.WaitForExit()
        if ($process.ExitCode) { throw "Capture extraction failed: $($capture.source)" }
        $process.Dispose()
    }
    Write-Output 'Inspect the captures, then run node tools/build-showcase.mjs.'
} finally { Pop-Location }
