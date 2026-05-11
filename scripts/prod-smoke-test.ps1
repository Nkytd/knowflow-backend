[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$Username = "tenant.admin",
    [string]$Password = "Tenant@123",
    [string]$OutputPath = ".\target\prod-smoke-test-report.json",
    [int]$ReadyTimeoutSeconds = 180,
    [int]$PollIntervalSeconds = 3
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding

$Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$BaseUrl = $BaseUrl.TrimEnd("/")

function Invoke-HttpCheck {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [int[]]$ExpectedStatusCodes = @(200)
    )

    $uri = "$BaseUrl$Path"
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -TimeoutSec 20
        $statusCode = [int]$response.StatusCode
        if ($ExpectedStatusCodes -notcontains $statusCode) {
            throw ("Unexpected status code. expected={0}, actual={1}" -f ($ExpectedStatusCodes -join ","), $statusCode)
        }

        return [pscustomobject]@{
            path = $Path
            statusCode = $statusCode
            ok = $true
        }
    }
    catch {
        $statusCode = 0
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode
            if ($ExpectedStatusCodes -contains $statusCode) {
                return [pscustomobject]@{
                    path = $Path
                    statusCode = $statusCode
                    ok = $true
                }
            }
        }

        return [pscustomobject]@{
            path = $Path
            statusCode = $statusCode
            ok = $false
            error = $_.Exception.Message
        }
    }
}

function Invoke-KnowFlowJson {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("GET", "POST")]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $Headers
        TimeoutSec = 30
        ErrorAction = "Stop"
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }

    return Invoke-RestMethod @params
}

function Assert-ApiOk {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Response,
        [Parameter(Mandatory = $true)]
        [string]$Action
    )

    if ($null -eq $Response) {
        throw "$Action returned no response."
    }

    if ($Response.code -ne 0) {
        throw ("{0} failed. code={1}, message={2}" -f $Action, $Response.code, $Response.message)
    }

    return $Response.data
}

function Get-PropertyValue {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Object,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [object]$Default = $null
    )

    if ($null -eq $Object) {
        return $Default
    }

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $Default
    }

    return $property.Value
}

function Wait-HealthReady {
    $deadline = (Get-Date).AddSeconds($ReadyTimeoutSeconds)
    $lastError = $null

    while ((Get-Date) -lt $deadline) {
        $check = Invoke-HttpCheck -Path "/actuator/health"
        if ($check.ok) {
            return $check
        }

        $lastError = $check.error
        Start-Sleep -Seconds $PollIntervalSeconds
    }

    throw ("Application did not become healthy within {0}s. lastError={1}" -f $ReadyTimeoutSeconds, $lastError)
}

$startedAt = Get-Date
$health = Wait-HealthReady

$pageChecks = @()
$pageChecks += Invoke-HttpCheck -Path "/admin/dashboard"
$pageChecks += Invoke-HttpCheck -Path "/assistant"
$pageChecks += Invoke-HttpCheck -Path "/swagger-ui.html" -ExpectedStatusCodes @(200, 302)

$failedPageChecks = @($pageChecks | Where-Object { -not $_.ok })
if ($failedPageChecks.Count -gt 0) {
    $failed = $failedPageChecks | ConvertTo-Json -Depth 4
    throw "Page smoke checks failed: $failed"
}

$loginData = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method POST -Path "/api/v1/auth/login" -Body @{
        username = $Username
        password = $Password
    }) `
    -Action "Login"

$token = [string]$loginData.token
if ([string]::IsNullOrWhiteSpace($token)) {
    throw "Login did not return a token."
}

$headers = @{ Authorization = "Bearer $token" }

$currentUser = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method GET -Path "/api/v1/auth/me" -Headers $headers) `
    -Action "Fetch current user"

$menus = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method GET -Path "/api/v1/auth/menus" -Headers $headers) `
    -Action "Fetch current menus"

$overview = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method GET -Path "/api/v1/admin/dashboard/overview" -Headers $headers) `
    -Action "Fetch dashboard overview"

$knowledgeBases = Assert-ApiOk `
    -Response (Invoke-KnowFlowJson -Method GET -Path "/api/v1/admin/knowledge-bases?pageNo=1&pageSize=1" -Headers $headers) `
    -Action "Fetch knowledge base page"

$report = [pscustomobject]@{
    baseUrl = $BaseUrl
    startedAt = $startedAt.ToString("yyyy-MM-dd HH:mm:ss")
    finishedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    health = $health
    pages = $pageChecks
    loginUser = [pscustomobject]@{
        username = [string](Get-PropertyValue -Object $currentUser -Name "username" -Default "")
        realName = [string](Get-PropertyValue -Object $currentUser -Name "realName" -Default "")
        tenantId = Get-PropertyValue -Object $currentUser -Name "tenantId"
        roles = @(Get-PropertyValue -Object $currentUser -Name "roleCodes" -Default @())
    }
    menuCount = @($menus).Count
    dashboard = [pscustomobject]@{
        qaCount = Get-PropertyValue -Object $overview -Name "qaCount"
        ticketCount = Get-PropertyValue -Object $overview -Name "ticketCount"
        knowledgeDraftCount = Get-PropertyValue -Object $overview -Name "knowledgeDraftCount"
    }
    knowledgeBaseTotal = Get-PropertyValue -Object $knowledgeBases -Name "total"
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([System.IO.Path]::IsPathRooted($OutputPath)) {
    $outputFullPath = $OutputPath
}
else {
    $outputFullPath = Join-Path $repoRoot $OutputPath
}

$outputDirectory = Split-Path -Parent $outputFullPath
if (-not (Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

$reportJson = $report | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($outputFullPath, $reportJson, $Utf8NoBom)

Write-Output "Production smoke test completed successfully."
Write-Output ("Report: {0}" -f $outputFullPath)
$reportJson
