[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',

    [string]$DocsPath = 'E:\WorkSpace\java\开发\knowledge document',

    [string]$Username = 'tenant.admin',

    [string]$Password = 'Tenant@123',

    [string]$KnowledgeBaseName = ("Knowledge Document Demo KB {0}" -f (Get-Date -Format 'yyyyMMdd-HHmmss')),

    [string]$Question = 'What data types does Redis support?',

    [string]$SessionTitle = ("LongCat Demo Session {0}" -f (Get-Date -Format 'HHmmss')),

    [int]$PollIntervalSeconds = 2,

    [int]$ParseTimeoutSeconds = 180
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function Assert-ApiSuccess {
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

function Invoke-KnowFlowJson {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('GET', 'POST', 'PUT', 'DELETE')]
        [string]$Method,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [object]$Body,

        [hashtable]$Headers = @{}
    )

    $uri = "{0}{1}" -f $BaseUrl.TrimEnd('/'), $Path
    $invokeParams = @{
        Uri         = $uri
        Method      = $Method
        Headers     = $Headers
        ErrorAction = 'Stop'
    }

    if ($null -ne $Body) {
        $invokeParams.ContentType = 'application/json'
        $invokeParams.Body = ($Body | ConvertTo-Json -Depth 8)
    }

    return Invoke-RestMethod @invokeParams
}

function Upload-KnowledgeDocument {
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
        $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)

        $content = [System.Net.Http.MultipartFormDataContent]::new()
        $content.Add([System.Net.Http.StringContent]::new([string]$KnowledgeBaseId), 'knowledgeBaseId')

        $stream = [System.IO.File]::OpenRead($FilePath)
        $fileContent = [System.Net.Http.StreamContent]::new($stream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('text/markdown')
        $content.Add($fileContent, 'file', [System.IO.Path]::GetFileName($FilePath))

        $uri = "{0}/api/v1/admin/documents/upload" -f $BaseUrl.TrimEnd('/')
        $response = $client.PostAsync($uri, $content).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $json = $raw | ConvertFrom-Json

        if (-not $response.IsSuccessStatusCode) {
            throw ("Upload failed for {0}. status={1}, body={2}" -f $FilePath, [int]$response.StatusCode, $raw)
        }

        if ($json.code -ne 0) {
            throw ("Upload failed for {0}. code={1}, message={2}" -f $FilePath, $json.code, $json.message)
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

function Wait-DocumentParseSuccess {
    param(
        [Parameter(Mandatory = $true)]
        [long]$DocumentId,

        [Parameter(Mandatory = $true)]
        [hashtable]$Headers
    )

    $deadline = (Get-Date).AddSeconds($ParseTimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        $document = Assert-ApiSuccess `
            -Response (Invoke-KnowFlowJson -Method GET -Path "/api/v1/admin/documents/$DocumentId" -Headers $Headers) `
            -Action ("Get document {0}" -f $DocumentId)

        if ($document.parseStatus -eq 'SUCCESS') {
            return $document
        }

        if ($document.parseStatus -eq 'FAILED') {
            throw ("Document {0} parse failed." -f $DocumentId)
        }

        Start-Sleep -Seconds $PollIntervalSeconds
    }

    throw ("Timed out waiting for document {0} parse success." -f $DocumentId)
}

if (-not (Test-Path $DocsPath)) {
    throw "Docs path not found: $DocsPath"
}

$docs = Get-ChildItem -Path $DocsPath -File -Filter '*.md' | Sort-Object Name
if ($docs.Count -eq 0) {
    throw "No markdown files found under: $DocsPath"
}

$loginData = Assert-ApiSuccess `
    -Response (Invoke-KnowFlowJson -Method POST -Path '/api/v1/auth/login' -Body @{
        username = $Username
        password = $Password
    }) `
    -Action 'Login'

$token = [string]$loginData.token
$headers = @{ Authorization = "Bearer $token" }

$knowledgeBase = Assert-ApiSuccess `
    -Response (Invoke-KnowFlowJson -Method POST -Path '/api/v1/admin/knowledge-bases' -Headers $headers -Body @{
        kbName      = $KnowledgeBaseName
        description = ("Imported from {0} at {1}" -f $DocsPath, (Get-Date -Format 's'))
    }) `
    -Action 'Create knowledge base'

$knowledgeBaseId = [long]$knowledgeBase.id
Write-Output ("Knowledge base created. id={0}, name={1}" -f $knowledgeBaseId, $knowledgeBase.kbName)

$importedDocuments = @()
foreach ($doc in $docs) {
    $upload = Upload-KnowledgeDocument -FilePath $doc.FullName -Token $token -KnowledgeBaseId $knowledgeBaseId
    Write-Output ("Uploaded {0} -> docId={1}, parseStatus={2}" -f $doc.Name, $upload.id, $upload.parseStatus)
    $importedDocuments += [pscustomobject]@{
        id       = [long]$upload.id
        docName  = [string]$upload.docName
        fileName = [string]$doc.Name
    }
}

$parsedDocuments = @()
foreach ($document in $importedDocuments) {
    $detail = Wait-DocumentParseSuccess -DocumentId $document.id -Headers $headers
    Write-Output ("Parsed {0} successfully. chunkCount={1}" -f $document.fileName, $detail.chunkCount)
    $parsedDocuments += [pscustomobject]@{
        id         = [long]$detail.id
        docName    = [string]$detail.docName
        chunkCount = [int]$detail.chunkCount
    }
}

$session = Assert-ApiSuccess `
    -Response (Invoke-KnowFlowJson -Method POST -Path '/api/v1/app/qa/sessions' -Headers $headers -Body @{
        knowledgeBaseId = $knowledgeBaseId
        sessionTitle    = $SessionTitle
    }) `
    -Action 'Create QA session'

$message = Assert-ApiSuccess `
    -Response (Invoke-KnowFlowJson -Method POST -Path '/api/v1/app/qa/messages' -Headers $headers -Body @{
        sessionId = [long]$session.id
        question  = $Question
    }) `
    -Action 'Ask question'

$sources = Assert-ApiSuccess `
    -Response (Invoke-KnowFlowJson -Method GET -Path ("/api/v1/app/qa/messages/{0}/sources" -f $message.id) -Headers $headers) `
    -Action 'Fetch QA sources'

$result = [pscustomobject]@{
    knowledgeBase = [pscustomobject]@{
        id   = $knowledgeBaseId
        name = $knowledgeBase.kbName
    }
    importedDocumentCount = $importedDocuments.Count
    parsedDocuments       = $parsedDocuments
    qaSession             = [pscustomobject]@{
        id    = [long]$session.id
        title = [string]$session.sessionTitle
    }
    question              = $Question
    answer                = [pscustomobject]@{
        id                = [long]$message.id
        answerStatus      = [string]$message.answerStatus
        modelName         = [string]$message.modelName
        needHumanHandoff  = [bool]$message.needHumanHandoff
        sourceCount       = [int]$message.sourceCount
        answerText        = [string]$message.answerText
    }
    sources               = $sources
}

$result | ConvertTo-Json -Depth 8
