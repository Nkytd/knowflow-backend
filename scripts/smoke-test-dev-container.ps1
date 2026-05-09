[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:18081",
    [string]$Username = "tenant.admin",
    [string]$Password = "Tenant@123",
    [string]$KnowledgeBaseName = ("Stage3 Smoke KB " + (Get-Date -Format "yyyyMMdd-HHmmss")),
    [string]$SessionTitle = "Stage3 Smoke Session",
    [string]$Question = "How should I handle a VPN login failure after a certificate update?",
    [string]$OutputPath = ".\\target\\smoke-test-report.json",
    [int]$PollIntervalSeconds = 2,
    [int]$ReadyTimeoutSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
Add-Type -AssemblyName System.Net.Http

$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$Utf8 = [System.Text.Encoding]::UTF8

function Read-Utf8ResponseBody {
    param(
        [Parameter(Mandatory = $true)]
        [System.Net.Http.HttpContent]$Content
    )

    $bytes = $Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    return $Utf8.GetString($bytes)
}

function Invoke-KnowFlowJson {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST", "PUT", "DELETE")]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Path,
        $Body = $null,
        [hashtable]$Headers = @{}
    )

    $client = [System.Net.Http.HttpClient]::new()
    $request = $null

    try {
        $request = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::$Method,
            ($BaseUrl.TrimEnd("/") + $Path)
        )

        foreach ($key in $Headers.Keys) {
            $request.Headers.TryAddWithoutValidation($key, [string]$Headers[$key]) | Out-Null
        }

        if ($null -ne $Body) {
            $jsonBody = $Body | ConvertTo-Json -Depth 8
            $request.Content = [System.Net.Http.StringContent]::new(
                $jsonBody,
                $Utf8NoBom,
                "application/json"
            )
        }

        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $raw = Read-Utf8ResponseBody -Content $response.Content
        if (-not $response.IsSuccessStatusCode) {
            throw ("{0} {1} failed. status={2}, body={3}" -f $Method, $Path, [int]$response.StatusCode, $raw)
        }

        return $raw | ConvertFrom-Json
    }
    finally {
        if ($request) {
            $request.Dispose()
        }
        $client.Dispose()
    }
}

function Assert-ApiOk {
    param(
        [Parameter(Mandatory = $true)]
        $Response,
        [Parameter(Mandatory = $true)]
        [string]$Action
    )

    if ($null -eq $Response) {
        throw "$Action returned null response."
    }
    if ($Response.code -ne 0) {
        throw ("{0} failed. code={1}, message={2}" -f $Action, $Response.code, $Response.message)
    }
    return $Response.data
}

function Upload-File {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string]$Token,
        [Parameter(Mandatory = $true)]
        [long]$KnowledgeBaseId
    )

    $client = [System.Net.Http.HttpClient]::new()
    $stream = $null
    $content = $null

    try {
        $client.DefaultRequestHeaders.Authorization =
            [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)

        $content = [System.Net.Http.MultipartFormDataContent]::new()
        $content.Add([System.Net.Http.StringContent]::new([string]$KnowledgeBaseId), "knowledgeBaseId")

        $stream = [System.IO.File]::OpenRead($FilePath)
        $fileContent = [System.Net.Http.StreamContent]::new($stream)
        $fileContent.Headers.ContentType =
            [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("text/markdown")
        $content.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))

        $response = $client.PostAsync(($BaseUrl.TrimEnd("/") + "/api/v1/admin/documents/upload"), $content).
            GetAwaiter().GetResult()
        $raw = Read-Utf8ResponseBody -Content $response.Content

        if (-not $response.IsSuccessStatusCode) {
            throw ("Upload failed. status={0}, body={1}" -f [int]$response.StatusCode, $raw)
        }

        $json = $raw | ConvertFrom-Json
        if ($json.code -ne 0) {
            throw ("Upload failed. code={0}, message={1}" -f $json.code, $json.message)
        }

        return $json.data
    }
    finally {
        if ($stream) {
            $stream.Dispose()
        }
        if ($content) {
            $content.Dispose()
        }
        $client.Dispose()
    }
}

function Wait-DocumentReady {
    param(
        [Parameter(Mandatory = $true)]
        [long]$DocumentId,
        [Parameter(Mandatory = $true)]
        [hashtable]$Headers
    )

    $deadline = (Get-Date).AddSeconds($ReadyTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $document = Assert-ApiOk `
            -Response (Invoke-KnowFlowJson -Method GET -Path "/api/v1/admin/documents/$DocumentId" -Headers $Headers) `
            -Action ("Get document {0}" -f $DocumentId)

        if ($document.parseStatus -eq "FAILED" -or $document.indexStatus -eq "FAILED") {
            throw ("Document {0} failed. parseStatus={1}, indexStatus={2}" -f $DocumentId, $document.parseStatus, $document.indexStatus)
        }

        if ($document.parseStatus -eq "SUCCESS" -and $document.indexStatus -eq "SUCCESS") {
            return $document
        }

        Start-Sleep -Seconds $PollIntervalSeconds
    }

    throw ("Timed out waiting for document {0} to become ready." -f $DocumentId)
}

$healthResponse = Invoke-WebRequest -UseBasicParsing -Uri ($BaseUrl.TrimEnd("/") + "/actuator/health") -TimeoutSec 20
if ($healthResponse.StatusCode -ne 200) {
    throw ("Health check failed. statusCode={0}" -f $healthResponse.StatusCode)
}

$login = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method POST -Path "/api/v1/auth/login" -Body @{
        username = $Username
        password = $Password
    }) `
    -Action "Login"

$token = [string]$login.token
$headers = @{
    Authorization = "Bearer $token"
}

$knowledgeBase = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method POST -Path "/api/v1/admin/knowledge-bases" -Headers $headers -Body @{
        kbName = $KnowledgeBaseName
        description = "Automated smoke test knowledge base"
    }) `
    -Action "Create knowledge base"

$repoRoot = Split-Path -Parent $PSScriptRoot
$tempDir = Join-Path $repoRoot "target\\smoke-test"
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
$filePath = Join-Path $tempDir "vpn-smoke-demo.md"

 $smokeMarkdown = @'
# VPN Certificate Recovery Guide

If the VPN login fails after a certificate update, ask the user to re-import the latest security certificate.
Then restart the desktop VPN client and try logging in again.
If DNS still fails, verify that the company gateway domain can be resolved locally.
'@
[System.IO.File]::WriteAllText($filePath, $smokeMarkdown, $Utf8NoBom)

$upload = Upload-File -FilePath $filePath -Token $token -KnowledgeBaseId ([long]$knowledgeBase.id)
$document = Wait-DocumentReady -DocumentId ([long]$upload.id) -Headers $headers

$session = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method POST -Path "/api/v1/app/qa/sessions" -Headers $headers -Body @{
        knowledgeBaseId = [long]$knowledgeBase.id
        sessionTitle = $SessionTitle
    }) `
    -Action "Create QA session"

$message = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method POST -Path "/api/v1/app/qa/messages" -Headers $headers -Body @{
        sessionId = [long]$session.id
        question = $Question
    }) `
    -Action "Ask question"

$messageDetail = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method GET -Path ("/api/v1/app/qa/messages/{0}" -f $message.id) -Headers $headers) `
    -Action "Get QA message detail"

$sources = @()
if ($null -ne $messageDetail.sources) {
    $sources = @($messageDetail.sources)
}
$firstSource = $null
if ($sources.Count -gt 0) {
    $firstSource = $sources[0]
}

$report = [pscustomobject]@{
    baseUrl = $BaseUrl
    generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    healthStatusCode = [int]$healthResponse.StatusCode
    knowledgeBaseId = [long]$knowledgeBase.id
    knowledgeBaseName = [string]$knowledgeBase.kbName
    documentId = [long]$document.id
    documentName = [string]$document.docName
    parseStatus = [string]$document.parseStatus
    indexStatus = [string]$document.indexStatus
    chunkCount = [int]$document.chunkCount
    qaSessionId = [long]$session.id
    qaMessageId = [long]$messageDetail.id
    answerStatus = [string]$messageDetail.answerStatus
    modelName = [string]$messageDetail.modelName
    needHumanHandoff = [bool]$messageDetail.needHumanHandoff
    sourceCount = [int]$messageDetail.sourceCount
    answerText = [string]$messageDetail.answerText
    firstSourceDocument = if ($null -ne $firstSource) { [string]$firstSource.documentName } else { "" }
    firstSourceScore = if ($null -ne $firstSource) { [double]$firstSource.recallScore } else { 0.0 }
    firstRecallStrategy = if ($null -ne $firstSource) { [string]$firstSource.recallStrategy } else { "" }
}

$outputFullPath = Join-Path $repoRoot $OutputPath
$outputDirectory = Split-Path -Parent $outputFullPath
if (-not (Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$reportJson = $report | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($outputFullPath, $reportJson, $Utf8NoBom)

Write-Output ("Smoke test completed successfully.")
Write-Output ("Report: {0}" -f $outputFullPath)
$report | ConvertTo-Json -Depth 8
