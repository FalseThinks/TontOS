<#
.SYNOPSIS
    TontOS Universal Containerized Build & Execution Engine (Windows PowerShell)
.DESCRIPTION
    Runs TontOS builds inside isolated Docker containers on Windows 10/11.
    Translates Windows paths and memory limits automatically.
    Maintainer: FalseThinks <paproken@protonmail.com>
#>

[CmdletBinding()]
param(
    [Parameter(Position=0)]
    [ValidateSet("build", "test", "apk", "shell", "clean", "help")]
    [string]$Action = "build",

    [switch]$Available,
    [switch]$Total,
    [ValidateSet("4", "8", "16", "32")]
    [string]$Ram = "",

    [string]$ImageName = "tontos-builder:latest"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$BinDir = Split-Path -Parent $ScriptDir
$RootDir = Split-Path -Parent $BinDir
$Dockerfile = Join-Path $RootDir "Dockerfile"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  TontOS Universal Containerized Engine (Windows)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Verify Docker Desktop is running
try {
    docker info | Out-Null
} catch {
    Write-Error "Docker is not running. Please start Docker Desktop for Windows and try again."
    exit 1
}

# 2. Query Windows Host Memory
$os = Get-CimInstance Win32_OperatingSystem
$totalRamGb = [math]::Round($os.TotalVisibleMemorySize / 1024 / 1024)
$availRamGb = [math]::Round($os.FreePhysicalMemory / 1024 / 1024)

# 3. Determine RAM profile & concurrency
if ($Ram -ne "") {
    $activeTier = $Ram
    $strategyDesc = "Manual Override (-Ram $Ram)"
} elseif ($Total) {
    if ($totalRamGb -le 6) { $activeTier = "4" }
    elseif ($totalRamGb -le 12) { $activeTier = "8" }
    elseif ($totalRamGb -le 24) { $activeTier = "16" }
    else { $activeTier = "32" }
    $strategyDesc = "Total PC Hardware Capacity (${totalRamGb}GB)"
} else {
    if ($availRamGb -le 6) { $activeTier = "4" }
    elseif ($availRamGb -le 12) { $activeTier = "8" }
    elseif ($availRamGb -le 24) { $activeTier = "16" }
    else { $activeTier = "32" }
    $strategyDesc = "Currently Available RAM (${availRamGb}GB free of ${totalRamGb}GB)"
}

switch ($activeTier) {
    "4" {
        $profileLabel = "4GB (Ultra-Low RAM)"
        $buildJobs = 1
        $javaXmx = "2g"
        $shmSize = "2g"
    }
    "8" {
        $profileLabel = "8GB (Low RAM / Balanced)"
        $buildJobs = 2
        $javaXmx = "4g"
        $shmSize = "4g"
    }
    "16" {
        $profileLabel = "16GB (Standard Workstation)"
        $buildJobs = 4
        $javaXmx = "6g"
        $shmSize = "4g"
    }
    "32" {
        $profileLabel = "32GB+ (High Performance)"
        $buildJobs = 8
        $javaXmx = "12g"
        $shmSize = "8g"
    }
}

Write-Host "  RAM Profile:   $profileLabel"
Write-Host "  RAM Strategy:  $strategyDesc"
Write-Host "  Concurrency:   $buildJobs jobs | Heap: -Xmx$javaXmx | Shared Memory: $shmSize"
Write-Host "==========================================================" -ForegroundColor Cyan

# Ensure internal directories
$buildDir = Join-Path $RootDir ".build"
$ccacheDir = Join-Path $RootDir ".ccache"
$outDir = Join-Path $RootDir "out"

if (-not (Test-Path $buildDir)) { New-Item -ItemType Directory -Path $buildDir | Out-Null }
if (-not (Test-Path $ccacheDir)) { New-Item -ItemType Directory -Path $ccacheDir | Out-Null }
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

# Build Docker image if missing
$imageCheck = docker images -q $ImageName
if (-not $imageCheck) {
    Write-Host "[+] Building Docker image $ImageName on Windows..." -ForegroundColor Yellow
    docker build -t $ImageName -f $Dockerfile $RootDir
}

# Convert Windows path for Docker volume mounting
$dockerWorkspace = ($RootDir -replace '\\', '/').Replace('C:', '//c').Replace('c:', '//c')
$dockerCcache = ($ccacheDir -replace '\\', '/').Replace('C:', '//c').Replace('c:', '//c')
$dockerOut = ($outDir -replace '\\', '/').Replace('C:', '//c').Replace('c:/', '//c/')

switch ($Action) {
    "help" {
        Write-Host "Commands:"
        Write-Host "  -Action build (default)  Compile AOSP ROM inside container"
        Write-Host "  -Action test             Run Rust test suite"
        Write-Host "  -Action apk              Build launcher APK"
        Write-Host "  -Action shell            Interactive bash inside container"
        Write-Host "  -Action clean            Clean build and out dirs"
        Write-Host "Options:"
        Write-Host "  -Available               Adapt to currently free RAM"
        Write-Host "  -Total                   Push full hardware RAM"
        Write-Host "  -Ram <4|8|16|32>         Force explicit RAM tier"
        exit 0
    }
    "clean" {
        Write-Host "[+] Cleaning internal build caches..."
        Remove-Item -Recurse -Force $buildDir, $ccacheDir, $outDir -ErrorAction SilentlyContinue
        Write-Host "[✓] Clean complete."
        exit 0
    }
    "shell" {
        docker run --rm -it `
            --shm-size="$shmSize" `
            -v "${dockerWorkspace}:/workspace" `
            -v "${dockerCcache}:/workspace/.ccache" `
            -v "${dockerOut}:/workspace/out" `
            -w "/workspace" `
            $ImageName /bin/bash
        exit 0
    }
    "test" {
        docker run --rm `
            -v "${dockerWorkspace}:/workspace" `
            -w "/workspace" `
            $ImageName cargo test --workspace
        exit 0
    }
    "apk" {
        docker run --rm `
            -v "${dockerWorkspace}:/workspace" `
            -w "/workspace" `
            $ImageName ./bin/build_launcher_apk.sh
        exit 0
    }
    "build" {
        Write-Host "[+] Launching containerized AOSP compilation (-j$buildJobs)..." -ForegroundColor Green
        docker run --rm `
            --shm-size="$shmSize" `
            -e BUILD_JOBS="$buildJobs" `
            -e _JAVA_OPTIONS="-Xmx$javaXmx" `
            -e USE_CCACHE=1 `
            -e CCACHE_DIR=/workspace/.ccache `
            -v "${dockerWorkspace}:/workspace" `
            -v "${dockerCcache}:/workspace/.ccache" `
            -v "${dockerOut}:/workspace/out" `
            -w "/workspace" `
            $ImageName /bin/bash -c "
                source build/envsetup.sh 2>/dev/null || true &&
                lunch tontos_cf_x86_64-trunk_staging-userdebug 2>/dev/null || true &&
                m -j$buildJobs droid
            "
        exit 0
    }
}
