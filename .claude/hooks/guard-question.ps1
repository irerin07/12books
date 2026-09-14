# 질문 하네스 (PreToolUse)
#
# AskUserQuestion을 가로채 "선택지가 같은 종류인가"를 한 번 되묻는다.
#
# 왜 있는가 — 열린 설계 공간을 객관식으로 바꿀 때, 가장 "결정처럼 보이는" 축(계정·키가
# 필요해서 되돌리기 어려운 쪽)만 질문으로 남기고 나머지 축은 암묵적 기본값으로 접히는 일이
# 있었다. 접힌 축의 기본값은 이 코드베이스의 기준이 아니라 도메인에서 가장 눈에 익은 것
# (벤더가 전면에 내세우는 방식)에서 왔다.
#
# 실제 사례: 메일 발송자를 물으면서 선택지를 [Resend, AWS SES, Gmail SMTP, 미정]으로 냈다.
# 앞의 둘은 "누가 보내는가"(발송자)이고 셋째는 "어떻게 말을 거는가"(전송 방식)라 분류가
# 섞여 있었다. 그 탓에 JavaMailSender(SMTP)가 하나의 발송자 성질처럼 보여 검토에서 빠졌고,
# "Gmail은 공개 서비스용으로 비권장"이 SMTP 전체에 대한 판정처럼 읽혔다.
#
# 무엇을 하는가 — 기계적으로 판정할 수 있는 두 가지만 본다. 나머지는 체크리스트로 돌려준다.
#
#   1. 분류 혼합  : 일부 라벨에만 전송·프로토콜 이름(SMTP·HTTP API·SDK…)이 들어 있다
#   2. 선택지 겹침: 한 라벨이 다른 라벨을 통째로 포함한다
#
# 걸리면 "한 번만" 되돌려 보낸다. 같은 질문을 다시 보내면 통과한다 — 강제하려는 것은
# 특정 답이 아니라 한 번의 재검토이고, 막기만 하면 오탐이 교착이 된다.
#
# 한계 — 이건 과속방지턱이지 판정기가 아니다. 접힌 축이 있는지, 기본값의 근거가 무엇인지는
# 기계가 알 수 없다. 오탐도 있다(다른 종류를 나란히 묻는 것이 맞는 질문일 때). 그래서 막지
# 않고 한 번 묻기만 한다.
#
# 주의: 이 파일은 UTF-8 BOM으로 저장해야 한다. Windows PowerShell 5.1은 BOM이 없으면
# 스크립트를 ANSI 코드페이지로 읽어 한글이 깨진다.
#
# 테스트: powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/guard-question.tests.ps1

$ErrorActionPreference = 'Stop'

# Claude Code는 훅의 stdout을 UTF-8로 읽는다. 콘솔 기본 인코딩(cp949)으로 내보내면 한글이 깨진다.
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

# 전송 방식·프로토콜·연동 형태의 이름. "무엇을 쓰는가"가 아니라 "어떻게 말을 거는가" 쪽 낱말들이다.
# 앞뒤를 영문자가 아닌 것으로 묶어 SES 안의 S, JSONP 같은 것에 걸리지 않게 한다.
$script:MechanismPattern = '(?i)(^|[^A-Za-z])(SMTP|IMAP|POP3|HTTPS?|REST|API|SDK|CLI|GraphQL|gRPC|WebSocket|Webhook|SSE|JMS|AMQP|MQTT|JDBC|ODBC|RPC|OAuth|JWT|SSH|SFTP|FTP|TCP|UDP|CSV|JSON|XML|YAML)([^A-Za-z]|$)'

# "고르지 않겠다"는 선택지. 이것까지 종류를 따지면 멀쩡한 질문이 걸린다.
$script:DeferPattern = '아직|나중|추후|보류|정하지|모르|기타|직접 입력|건너'

function Allow {
    exit 0
}

function Ask([string]$reason) {
    $decision = @{
        hookSpecificOutput = @{
            hookEventName            = 'PreToolUse'
            permissionDecision       = 'deny'
            permissionDecisionReason = $reason
        }
    }
    $decision | ConvertTo-Json -Depth 5 -Compress
    exit 0
}

$script:Checklist = @"
질문을 보내기 전에 세 가지를 확인하세요.

1. 이 선택지들이 같은 종류인가.
   제품(Resend·SES)과 전송 방식(SMTP·HTTP API)처럼 다른 축이 한 목록에 섞이면,
   한 축이 다른 축의 성질처럼 보여 검토에서 조용히 빠집니다.

2. 접기로 한 축이 있으면 접었다고 말했는가.
   "발송자만 정해 주세요 - 전송은 SMTP로 잡겠습니다"처럼 한 줄이면 됩니다.
   혼자 정할 수 있는 축이라도, 정하지 않은 채 기본값을 물려받는 일을 막습니다.

3. 그 기본값의 근거가 이 코드베이스인가.
   가장 눈에 익은 것(벤더가 내세우는 방식, 튜토리얼에 흔한 것)이 그대로
   들어오지 않았는지. 교체 비용·표준 API·새로 생기는 개념 수로 따져 보세요.

되돌아본 뒤 그대로가 맞으면 같은 질문을 다시 보내면 통과합니다.
"@

function Get-Labels($question) {
    $labels = @()
    foreach ($option in @($question.options)) {
        $label = [string]$option.label
        if (-not [string]::IsNullOrWhiteSpace($label)) { $labels += $label }
    }
    return , $labels
}

# 일부 라벨에만 전송·프로토콜 이름이 있으면 분류가 섞였을 수 있다.
# 전부 있거나 전부 없으면 같은 축을 묻고 있는 것이다.
function Test-MixedKinds([string[]]$labels) {
    $considered = @($labels | Where-Object { $_ -notmatch $script:DeferPattern })
    if ($considered.Count -lt 2) { return $false }
    $withMechanism = @($considered | Where-Object { $_ -match $script:MechanismPattern })
    return ($withMechanism.Count -gt 0 -and $withMechanism.Count -lt $considered.Count)
}

# 한 라벨이 다른 라벨을 통째로 품으면 상호 배타가 아니다 - 고르는 사람이 둘의 차이를
# 추측하게 된다.
function Test-Overlap([string[]]$labels) {
    $normalized = @()
    foreach ($label in $labels) {
        $normalized += ($label -replace '\(추천\)', '' -replace '\(Recommended\)', '' -replace '\s', '').ToLowerInvariant()
    }
    for ($i = 0; $i -lt $normalized.Count; $i++) {
        for ($j = $i + 1; $j -lt $normalized.Count; $j++) {
            if ([string]::IsNullOrWhiteSpace($normalized[$i]) -or [string]::IsNullOrWhiteSpace($normalized[$j])) { continue }
            if ($normalized[$i] -eq $normalized[$j]) { return $true }
            if ($normalized[$i].Contains($normalized[$j]) -or $normalized[$j].Contains($normalized[$i])) { return $true }
        }
    }
    return $false
}

# 같은 질문을 두 번째로 보내면 통과시킨다. 강제하려는 것은 특정 답이 아니라 한 번의
# 재검토라, 계속 막으면 오탐이 곧 교착이 된다.
function Test-AlreadyAsked([string]$key) {
    $root = [string]$env:CLAUDE_GUARD_QUESTION_CACHE
    if ([string]::IsNullOrWhiteSpace($root)) {
        $root = Join-Path ([System.IO.Path]::GetTempPath()) 'claude-guard-question'
    }
    New-Item -ItemType Directory -Path $root -Force | Out-Null

    # 오래된 표시는 지운다. 하루가 지난 질문은 다시 물어도 된다.
    Get-ChildItem -Path $root -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-1) } |
        Remove-Item -Force -ErrorAction SilentlyContinue

    $marker = Join-Path $root $key
    if (Test-Path $marker) { return $true }
    New-Item -ItemType File -Path $marker -Force | Out-Null
    return $false
}

function Get-Key($payload) {
    $session = [string]$payload.session_id
    if ([string]::IsNullOrWhiteSpace($session)) { $session = 'nosession' }
    $json = $payload.tool_input | ConvertTo-Json -Depth 10 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($session + '|' + $json)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($sha.ComputeHash($bytes)).Replace('-', '')
    }
    finally {
        $sha.Dispose()
    }
}

# 훅이 어떤 이유로든 깨지면 질문을 막지 않는다. 보호 장치가 대화를 인질로 잡으면 안 된다.
try {
    # stdin을 UTF-8로 "직접" 읽는다. [Console]::In은 콘솔 입력 인코딩으로 디코딩하는데
    # 그 기본값이 한국어 Windows에서는 cp949, GitHub 러너에서는 437이라 한글 라벨이 통째로
    # 깨져 도착한다. 그러면 "아직 정하지 않는다" 같은 보류 선택지를 알아보지 못해 멀쩡한
    # 질문이 걸린다 - 실제로 CI에서 그렇게 드러났다.
    $stdin = New-Object System.IO.StreamReader(
        [Console]::OpenStandardInput(), (New-Object System.Text.UTF8Encoding($false)))
    try {
        $raw = $stdin.ReadToEnd()
    }
    finally {
        $stdin.Dispose()
    }
    if ([string]::IsNullOrWhiteSpace($raw)) { Allow }
    $payload = $raw | ConvertFrom-Json

    if ([string]$payload.tool_name -ne 'AskUserQuestion') { Allow }

    $findings = @()
    foreach ($question in @($payload.tool_input.questions)) {
        $labels = Get-Labels $question
        if ($labels.Count -lt 2) { continue }
        $headline = [string]$question.header
        if ([string]::IsNullOrWhiteSpace($headline)) { $headline = [string]$question.question }

        if (Test-MixedKinds $labels) {
            $findings += "[$headline] 일부 선택지에만 전송·프로토콜 이름이 있습니다: " + ($labels -join ' / ')
        }
        if (Test-Overlap $labels) {
            $findings += "[$headline] 한 선택지가 다른 선택지를 품고 있습니다: " + ($labels -join ' / ')
        }
    }

    if ($findings.Count -eq 0) { Allow }
    if (Test-AlreadyAsked (Get-Key $payload)) { Allow }

    Ask (($findings -join "`n") + "`n`n" + $script:Checklist)
}
catch {
    Allow
}

Allow
