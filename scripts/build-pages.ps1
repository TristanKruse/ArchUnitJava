$ErrorActionPreference = 'Stop'

$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$siteRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'target\pages'))
$expectedSiteRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'target\pages'))
$apiSource = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'target\reports\apidocs'))

if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -PathType Leaf)) {
    throw 'Could not locate the ArchUnitJava repository root.'
}

if (-not (Test-Path -LiteralPath (Join-Path $apiSource 'index.html') -PathType Leaf)) {
    throw "Javadocs are missing; run Maven's javadoc:javadoc goal first."
}

if (-not $siteRoot.Equals($expectedSiteRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Refusing to write outside target/pages.'
}

if (Test-Path -LiteralPath $siteRoot) {
    Remove-Item -LiteralPath $siteRoot -Recurse -Force
}

New-Item -ItemType Directory -Path (Join-Path $siteRoot 'api') -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\site\index.html') -Destination $siteRoot
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\site\styles.css') -Destination $siteRoot
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'docs\site\favicon.svg') -Destination $siteRoot
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'assets\logo-rounded.png') -Destination $siteRoot
Copy-Item -LiteralPath (Join-Path $repositoryRoot 'assets\social-preview.png') -Destination $siteRoot
Copy-Item -Path (Join-Path $apiSource '*') -Destination (Join-Path $siteRoot 'api') -Recurse
New-Item -ItemType File -Path (Join-Path $siteRoot '.nojekyll') -Force | Out-Null

$landingPath = Join-Path $siteRoot 'index.html'
$landingHtml = Get-Content -LiteralPath $landingPath -Raw
$references = [regex]::Matches($landingHtml, '(?:href|src)="([^"]+)"') |
    ForEach-Object { $_.Groups[1].Value }

foreach ($reference in $references) {
    if ($reference -match '^(?:https?:|mailto:|tel:)') {
        continue
    }

    $parts = $reference.Split('#', 2)
    $relativePath = $parts[0]
    $fragment = if ($parts.Count -eq 2) { $parts[1] } else { '' }

    if ($relativePath.Length -gt 0) {
        if ($relativePath.EndsWith('/')) {
            $relativePath += 'index.html'
        }
        $resolvedReference = [System.IO.Path]::GetFullPath((Join-Path $siteRoot $relativePath))
        if (-not $resolvedReference.StartsWith(
                $siteRoot + [System.IO.Path]::DirectorySeparatorChar,
                [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Documentation reference escapes the site root: $reference"
        }
        if (-not (Test-Path -LiteralPath $resolvedReference)) {
            throw "Broken documentation reference: $reference"
        }
    }

    if ($fragment.Length -gt 0 -and $landingHtml -notmatch ('id="' + [regex]::Escape($fragment) + '"')) {
        throw "Broken documentation fragment: $reference"
    }
}

Write-Output "Documentation site assembled at $siteRoot"
