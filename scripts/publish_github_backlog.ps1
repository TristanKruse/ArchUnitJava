param(
    [Parameter(Mandatory = $true)]
    [string] $Repository
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$labelSpecs = Get-Content -LiteralPath (Join-Path $root '.archunitdev\github-labels.json') -Raw | ConvertFrom-Json
$issueSpecs = Get-Content -LiteralPath (Join-Path $root '.archunitdev\github-issues.json') -Raw | ConvertFrom-Json

gh repo view $Repository --json nameWithOwner,visibility | Out-Null

foreach ($label in $labelSpecs) {
    gh label create $label.name --repo $Repository --color $label.color --description $label.description --force | Out-Null
}

$existing = @(gh issue list --repo $Repository --state all --limit 200 --json number,title,state | ConvertFrom-Json)
$byNumber = @{}
foreach ($issue in $existing) {
    $byNumber[[int] $issue.number] = $issue
}

foreach ($spec in $issueSpecs) {
    $number = [int] $spec.number
    $bodyPath = Join-Path $root ($spec.body_file -replace '/', '\')
    $labels = [string]::Join(',', @($spec.labels))

    if ($byNumber.ContainsKey($number)) {
        if ($byNumber[$number].title -ne $spec.title) {
            throw "Issue #$number exists with unexpected title '$($byNumber[$number].title)'"
        }
        gh issue edit $number --repo $Repository --body-file $bodyPath --add-label $labels | Out-Null
    }
    else {
        $url = gh issue create --repo $Repository --title $spec.title --body-file $bodyPath --label $labels
        if ($url -notmatch '/issues/(\d+)$' -or [int] $Matches[1] -ne $number) {
            throw "Expected new issue #$number but GitHub returned '$url'"
        }
    }

    if ([bool] $spec.initially_completed) {
        gh issue close $number --repo $Repository --reason completed | Out-Null
    }
}

$result = gh issue list --repo $Repository --state all --limit 200 --json number,title,state,labels | ConvertFrom-Json
$summary = [pscustomobject]@{
    Repository = $Repository
    Total = @($result).Count
    Open = @($result | Where-Object state -eq 'OPEN').Count
    Closed = @($result | Where-Object state -eq 'CLOSED').Count
    HighestNumber = ($result | Measure-Object -Property number -Maximum).Maximum
}
$summary | ConvertTo-Json

