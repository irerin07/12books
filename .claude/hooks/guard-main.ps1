# main 브랜치 보호 훅 (PreToolUse)
#
# stdin으로 훅 페이로드 JSON을 받아 stdout으로 결정 JSON을 돌려준다.
# 현재 브랜치가 main일 때만 개입하고, 그 외에는 조용히 통과시킨다.
#
#   - main에서 소스/설정 파일 수정   -> deny
#   - main에서 git commit / git push -> deny
#
# 계획 문서(*.md)와 .claude/ 설정은 main에서도 손볼 수 있게 허용한다.
#
# 주의: 이 파일은 UTF-8 BOM으로 저장해야 한다. Windows PowerShell 5.1은 BOM이 없으면
# 스크립트를 ANSI 코드페이지로 읽어 한글이 깨진다.

$ErrorActionPreference = 'Stop'

# Claude Code는 훅의 stdout을 UTF-8로 읽는다. 콘솔 기본 인코딩(cp949)으로 내보내면 한글이 깨진다.
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

function Allow {
    exit 0
}

function Deny([string]$reason) {
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

$branchHint = @"
main 브랜치에서는 이 작업을 할 수 없습니다.

먼저 작업 브랜치를 만드세요:
  git switch -c feat/<주제>

기능 하나를 통째로 시작한다면 /feature <설명> 을 쓰면 브랜치 생성부터 PR까지 처리합니다.
"@

# 훅이 어떤 이유로든 깨지면 작업을 막지 않는다. 보호 장치가 개발을 인질로 잡으면 안 된다.
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { Allow }
    $payload = $raw | ConvertFrom-Json

    $root = [string]$env:CLAUDE_PROJECT_DIR
    if ([string]::IsNullOrWhiteSpace($root)) { $root = (Get-Location).Path }

    $branch = (& git -C $root rev-parse --abbrev-ref HEAD 2>$null)
    if ($LASTEXITCODE -ne 0 -or $branch -ne 'main') { Allow }

    $tool = [string]$payload.tool_name

    if ($tool -in @('Edit', 'Write', 'MultiEdit', 'NotebookEdit')) {
        $target = [string]$payload.tool_input.file_path
        if ([string]::IsNullOrWhiteSpace($target)) { Allow }

        # 절대 경로를 프로젝트 기준 상대 경로로 정규화
        $normalized = $target.Replace('\', '/')
        $rootNorm = $root.Replace('\', '/').TrimEnd('/')
        if ($normalized.Length -gt $rootNorm.Length -and $normalized.StartsWith($rootNorm, [System.StringComparison]::OrdinalIgnoreCase)) {
            $normalized = $normalized.Substring($rootNorm.Length).TrimStart('/')
        }

        # main에서도 허용: 문서와 Claude 설정
        if ($normalized -like '.claude/*') { Allow }
        if ($normalized -like '*.md') { Allow }

        Deny "$branchHint`n(차단된 대상: $normalized)"
    }

    if ($tool -eq 'Bash' -or $tool -eq 'PowerShell') {
        $command = [string]$payload.tool_input.command
        if ([string]::IsNullOrWhiteSpace($command)) { Allow }

        if ($command -match '(^|[;&|]\s*)git\s+(-C\s+\S+\s+)?commit\b') {
            Deny "$branchHint`n(차단된 명령: git commit)"
        }
        if ($command -match '(^|[;&|]\s*)git\s+(-C\s+\S+\s+)?push\b') {
            Deny "main을 origin으로 직접 push할 수 없습니다. 브랜치에서 작업하고 PR로 올리세요.`n(차단된 명령: git push)"
        }
    }
}
catch {
    Allow
}

Allow
