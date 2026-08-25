param(
    [Parameter(Mandatory = $false)]
    [string]$JdkHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repository = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$fixtures = [System.IO.Path]::GetFullPath((Join-Path $repository 'test-fixtures\extraction'))
if (-not $fixtures.StartsWith($repository + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Fixture output escaped the repository.'
}
$javac = Join-Path $JdkHome 'bin\javac.exe'
if (-not (Test-Path -LiteralPath $javac -PathType Leaf)) {
    throw 'A JDK 25 JAVA_HOME (or -JdkHome) is required.'
}

function Write-HexFixture {
    param([string]$ClassFile, [string]$Destination)
    $resolved = [System.IO.Path]::GetFullPath($Destination)
    if (-not $resolved.StartsWith($fixtures + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Generated fixture escaped test-fixtures/extraction.'
    }
    $hex = [System.Convert]::ToHexString([System.IO.File]::ReadAllBytes($ClassFile)).ToLowerInvariant()
    [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($resolved)) | Out-Null
    [System.IO.File]::WriteAllText($resolved, $hex + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
}

$temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$scratch = [System.IO.Path]::GetFullPath((Join-Path $temporaryRoot ('archunitjava-corpus-' + [guid]::NewGuid().ToString('N'))))
if (-not $scratch.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Scratch directory escaped the system temporary directory.'
}
New-Item -ItemType Directory -Path $scratch | Out-Null
try {
    foreach ($release in 8..25) {
        $output = Join-Path $scratch ('release-' + $release)
        New-Item -ItemType Directory -Path $output | Out-Null
        & $javac --release $release -g -parameters -d $output (Join-Path $fixtures 'sources\java8\corpus\JavacFixture.java')
        if ($LASTEXITCODE -ne 0) { throw "javac failed for --release $release" }
        Write-HexFixture (Join-Path $output 'corpus\JavacFixture.class') (Join-Path $fixtures ("generated\releases\java-$release.hex"))
    }

    $moduleOutput = Join-Path $scratch 'module'
    New-Item -ItemType Directory -Path $moduleOutput | Out-Null
    & $javac --release 9 -d $moduleOutput (Join-Path $fixtures 'sources\java9\module-info.java')
    if ($LASTEXITCODE -ne 0) { throw 'javac failed for the module fixture' }
    Write-HexFixture (Join-Path $moduleOutput 'module-info.class') (Join-Path $fixtures 'generated\constructs\module-info.hex')

    $modernOutput = Join-Path $scratch 'modern'
    New-Item -ItemType Directory -Path $modernOutput | Out-Null
    & $javac --release 17 -g -parameters -d $modernOutput (Join-Path $fixtures 'sources\java17\corpus\ModernFixture.java') (Join-Path $fixtures 'sources\java17\corpus\Shape.java')
    if ($LASTEXITCODE -ne 0) { throw 'javac failed for modern fixtures' }
    Write-HexFixture (Join-Path $modernOutput 'corpus\ModernFixture.class') (Join-Path $fixtures 'generated\constructs\record.hex')
    Write-HexFixture (Join-Path $modernOutput 'corpus\Shape.class') (Join-Path $fixtures 'generated\constructs\sealed.hex')

    [System.IO.Directory]::CreateDirectory((Join-Path $fixtures 'generated\malformed')) | Out-Null
    [System.IO.File]::WriteAllText((Join-Path $fixtures 'generated\malformed\truncated.hex'), 'cafebabe00000045' + [Environment]::NewLine, [System.Text.UTF8Encoding]::new($false))
} finally {
    if (Test-Path -LiteralPath $scratch) {
        $verifiedScratch = [System.IO.Path]::GetFullPath($scratch)
        if (-not $verifiedScratch.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'Refusing to remove a scratch directory outside the system temporary directory.'
        }
        Remove-Item -LiteralPath $scratch -Recurse -Force
    }
}
