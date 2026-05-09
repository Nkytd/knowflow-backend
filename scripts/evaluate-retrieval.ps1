[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8081",
    [string]$Username = "tenant.admin",
    [string]$Password = "Tenant@123",
    [Parameter(Mandatory = $true)]
    [long]$KnowledgeBaseId,
    [string]$CasesFile = ".\docs\retrieval-eval-cases.sample.json",
    [string]$OutputPath = ".\target\retrieval-eval-report.json"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-KnowFlowApi {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Url,
        [hashtable]$Headers,
        $Body
    )

    $params = @{
        Method      = $Method
        Uri         = $Url
        ContentType = "application/json"
    }
    if ($Headers) {
        $params.Headers = $Headers
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    return Invoke-RestMethod @params
}

function Contains-AllKeywords {
    param(
        [string]$Text,
        [object[]]$Keywords
    )

    if (-not $Keywords -or $Keywords.Count -eq 0) {
        return $true
    }
    $normalized = ""
    if (-not [string]::IsNullOrWhiteSpace($Text)) {
        $normalized = $Text.ToLowerInvariant()
    }
    foreach ($keyword in $Keywords) {
        $value = [string]$keyword
        if ([string]::IsNullOrWhiteSpace($value)) {
            continue
        }
        if (-not $normalized.Contains($value.ToLowerInvariant())) {
            return $false
        }
    }
    return $true
}

function Contains-AnyKeyword {
    param(
        [string]$Text,
        [object[]]$Keywords
    )

    if (-not $Keywords -or $Keywords.Count -eq 0) {
        return $true
    }
    $normalized = ""
    if (-not [string]::IsNullOrWhiteSpace($Text)) {
        $normalized = $Text.ToLowerInvariant()
    }
    foreach ($keyword in $Keywords) {
        $value = [string]$keyword
        if ([string]::IsNullOrWhiteSpace($value)) {
            continue
        }
        if ($normalized.Contains($value.ToLowerInvariant())) {
            return $true
        }
    }
    return $false
}

function Get-MissingKeywords {
    param(
        [string]$Text,
        [object[]]$Keywords
    )

    $missing = @()
    if (-not $Keywords -or $Keywords.Count -eq 0) {
        return $missing
    }
    $normalized = ""
    if (-not [string]::IsNullOrWhiteSpace($Text)) {
        $normalized = $Text.ToLowerInvariant()
    }
    foreach ($keyword in $Keywords) {
        $value = [string]$keyword
        if ([string]::IsNullOrWhiteSpace($value)) {
            continue
        }
        if (-not $normalized.Contains($value.ToLowerInvariant())) {
            $missing += $value
        }
    }
    return $missing
}

function Get-Rate {
    param(
        [int]$Numerator,
        [int]$Denominator
    )

    if ($Denominator -eq 0) {
        return 0
    }
    return [math]::Round(($Numerator * 100.0 / $Denominator), 2)
}

function Matches-ExpectedSource {
    param(
        [string]$DocumentName,
        [object[]]$ExpectedPatterns
    )

    if (-not $ExpectedPatterns -or $ExpectedPatterns.Count -eq 0) {
        return $true
    }
    $name = ""
    if (-not [string]::IsNullOrWhiteSpace($DocumentName)) {
        $name = $DocumentName.ToLowerInvariant()
    }
    foreach ($pattern in $ExpectedPatterns) {
        $value = [string]$pattern
        if ([string]::IsNullOrWhiteSpace($value)) {
            continue
        }
        if ($name.Contains($value.ToLowerInvariant())) {
            return $true
        }
    }
    return $false
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$casesPath = Join-Path $repoRoot $CasesFile
$reportPath = Join-Path $repoRoot $OutputPath

if (-not (Test-Path $casesPath)) {
    throw "Cases file not found: $casesPath"
}

$cases = Get-Content -Raw $casesPath | ConvertFrom-Json
if (-not $cases -or $cases.Count -eq 0) {
    throw "No cases found in: $casesPath"
}

$loginResponse = Invoke-KnowFlowApi -Method POST -Url "$BaseUrl/api/v1/auth/login" -Body @{
    username = $Username
    password = $Password
}

if ($loginResponse.code -ne 0) {
    throw "Login failed: $($loginResponse.message)"
}

$token = $loginResponse.data.token
$headers = @{
    Authorization = "Bearer $token"
}

$results = @()

foreach ($caseItem in $cases) {
    $sessionResponse = Invoke-KnowFlowApi -Method POST -Url "$BaseUrl/api/v1/app/qa/sessions" -Headers $headers -Body @{
        knowledgeBaseId = $KnowledgeBaseId
        sessionTitle    = [string]$caseItem.sessionTitle
    }

    $sessionId = [long]$sessionResponse.data.id

    $askResponse = Invoke-KnowFlowApi -Method POST -Url "$BaseUrl/api/v1/app/qa/messages" -Headers $headers -Body @{
        sessionId = $sessionId
        question  = [string]$caseItem.question
    }

    $message = $askResponse.data
    $sources = @()
    if ($message.sources) {
        $sources = @($message.sources)
    }

    $topSource = $null
    if ($sources.Count -gt 0) {
        $topSource = $sources[0]
    }

    $expectedStatus = [string]$caseItem.expectedStatus
    $expectedMinSourceCount = 0
    if ($caseItem.PSObject.Properties.Name -contains "expectedMinSourceCount") {
        $expectedMinSourceCount = [int]$caseItem.expectedMinSourceCount
    }

    $statusMatch = $message.answerStatus -eq $expectedStatus
    $sourceCountMatch = [int]$message.sourceCount -ge $expectedMinSourceCount
    $sourceMatch = Matches-ExpectedSource -DocumentName ([string]$topSource.documentName) -ExpectedPatterns $caseItem.expectedSourceDocContains
    $keywordMatch = Contains-AllKeywords -Text ([string]$message.answerText) -Keywords $caseItem.expectedKeywords
    $topSnippetText = if ($topSource) { [string]$topSource.snippetText } else { "" }
    $snippetKeywordMatch = Contains-AnyKeyword -Text $topSnippetText -Keywords $caseItem.expectedSnippetKeywords
    $passed = $statusMatch -and $sourceCountMatch -and $sourceMatch -and $keywordMatch -and $snippetKeywordMatch
    $failureReasons = @()
    if (-not $statusMatch) {
        $failureReasons += ("status expected={0}, actual={1}" -f $expectedStatus, $message.answerStatus)
    }
    if (-not $sourceCountMatch) {
        $failureReasons += ("sourceCount expected>={0}, actual={1}" -f $expectedMinSourceCount, $message.sourceCount)
    }
    if (-not $sourceMatch) {
        $failureReasons += ("topSourceDocument not matched: {0}" -f ([string]$topSource.documentName))
    }
    if (-not $keywordMatch) {
        $missingKeywords = Get-MissingKeywords -Text ([string]$message.answerText) -Keywords $caseItem.expectedKeywords
        $failureReasons += ("missing answer keywords: {0}" -f (($missingKeywords -join ", ")))
    }
    if (-not $snippetKeywordMatch) {
        $failureReasons += "top snippet did not contain expected evidence keywords"
    }
    $answerText = [string]$message.answerText
    $answerPreview = $answerText
    if ($answerPreview.Length -gt 160) {
        $answerPreview = $answerPreview.Substring(0, 160) + "..."
    }

    $results += [pscustomobject]@{
        name                = [string]$caseItem.name
        category            = [string]$caseItem.category
        question            = [string]$caseItem.question
        expectedStatus      = $expectedStatus
        actualStatus        = [string]$message.answerStatus
        sourceCount         = [int]$message.sourceCount
        topSourceDocument   = if ($topSource) { [string]$topSource.documentName } else { "" }
        topRecallScore      = if ($topSource) { [double]$topSource.recallScore } else { 0.0 }
        topLexicalScore     = if ($topSource) { [double]$topSource.lexicalScore } else { 0.0 }
        topVectorScore      = if ($topSource) { [double]$topSource.vectorScore } else { 0.0 }
        recallStrategy      = if ($topSource) { [string]$topSource.recallStrategy } else { "" }
        statusMatch         = $statusMatch
        sourceCountMatch    = $sourceCountMatch
        keywordMatch        = $keywordMatch
        sourceMatch         = $sourceMatch
        snippetKeywordMatch = $snippetKeywordMatch
        passed              = $passed
        failureReasons      = $failureReasons
        answerPreview       = $answerPreview
    }
}

$passedCount = @($results | Where-Object { $_.passed }).Count
$statusPassedCount = @($results | Where-Object { $_.statusMatch }).Count
$sourceCountPassedCount = @($results | Where-Object { $_.sourceCountMatch }).Count
$sourcePassedCount = @($results | Where-Object { $_.sourceMatch }).Count
$keywordPassedCount = @($results | Where-Object { $_.keywordMatch }).Count
$snippetPassedCount = @($results | Where-Object { $_.snippetKeywordMatch }).Count
$successCases = @($results | Where-Object { $_.expectedStatus -eq "SUCCESS" })
$noHitCases = @($results | Where-Object { $_.expectedStatus -eq "NO_HIT" })
$successPassedCount = @($successCases | Where-Object { $_.passed }).Count
$noHitPassedCount = @($noHitCases | Where-Object { $_.passed }).Count
$categorySummaries = @()
foreach ($group in ($results | Group-Object category)) {
    $groupPassed = @($group.Group | Where-Object { $_.passed }).Count
    $categorySummaries += [pscustomobject]@{
        category = [string]$group.Name
        total    = [int]$group.Count
        passed   = [int]$groupPassed
        passRate = Get-Rate -Numerator $groupPassed -Denominator $group.Count
    }
}
$summary = [pscustomobject]@{
    baseUrl                 = $BaseUrl
    knowledgeBaseId         = $KnowledgeBaseId
    totalCases              = $results.Count
    passedCases             = $passedCount
    failedCases             = $results.Count - $passedCount
    passRate                = Get-Rate -Numerator $passedCount -Denominator $results.Count
    statusAccuracy          = Get-Rate -Numerator $statusPassedCount -Denominator $results.Count
    sourceCountAccuracy     = Get-Rate -Numerator $sourceCountPassedCount -Denominator $results.Count
    top1SourceAccuracy      = Get-Rate -Numerator $sourcePassedCount -Denominator $results.Count
    answerKeywordCoverage   = Get-Rate -Numerator $keywordPassedCount -Denominator $results.Count
    evidenceKeywordCoverage = Get-Rate -Numerator $snippetPassedCount -Denominator $results.Count
    successCasePassRate     = Get-Rate -Numerator $successPassedCount -Denominator $successCases.Count
    noHitCasePassRate       = Get-Rate -Numerator $noHitPassedCount -Denominator $noHitCases.Count
    generatedAt             = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
}

$report = [pscustomobject]@{
    summary          = $summary
    categorySummary  = $categorySummaries
    failedCases      = @($results | Where-Object { -not $_.passed })
    results          = $results
}

$reportDirectory = Split-Path -Parent $reportPath
if (-not (Test-Path $reportDirectory)) {
    New-Item -ItemType Directory -Path $reportDirectory | Out-Null
}

$report | ConvertTo-Json -Depth 8 | Set-Content -Path $reportPath -Encoding UTF8

Write-Output ("Evaluation completed. Pass rate: {0}% ({1}/{2})" -f $summary.passRate, $summary.passedCases, $summary.totalCases)
Write-Output ("Status accuracy: {0}%, Top1 source accuracy: {1}%, Evidence keyword coverage: {2}%" -f $summary.statusAccuracy, $summary.top1SourceAccuracy, $summary.evidenceKeywordCoverage)
Write-Output ("Report: {0}" -f $reportPath)
$results | Format-Table name, category, actualStatus, sourceCount, topSourceDocument, passed -AutoSize

if ($summary.failedCases -gt 0) {
    Write-Output "Failed cases:"
    $results | Where-Object { -not $_.passed } | ForEach-Object {
        Write-Output ("- {0}: {1}" -f $_.name, ($_.failureReasons -join "; "))
    }
}
