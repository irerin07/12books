# main 브랜치 보호 훅 (PreToolUse)
#
# stdin으로 훅 페이로드 JSON을 받아 stdout으로 결정 JSON을 돌려준다.
#
#   - 한 명령 안에서 main 전환 "뒤에" git 쓰기 작업  -> deny (현재 브랜치와 무관하게)
#   - main에서 소스/설정 파일 수정                   -> deny
#   - main에서 git commit / push / merge / rebase    -> deny
#
# 계획 문서(*.md)와 .claude/ 설정은 main에서도 손볼 수 있게 허용한다.
#
# 명령은 정규식으로 훑지 않고 셸 토큰으로 쪼개서 본다. 정규식으로는
# git "switch" main, git switch 'main', git checkout refs/heads/main 같은
# 정상적인 표기를 전부 따라갈 수 없고, 반대로 git 사용법을 설명하는 문장을 오탐한다.
# 토큰으로 보면 "이 세그먼트의 첫 토큰이 git인가"를 물을 수 있어 둘 다 해결된다.
#
# --- 이 훅의 한계 ------------------------------------------------------------
# 이건 에이전트를 위한 과속방지턱이지 보안 경계가 아니다. 훅은 도구 호출 "전"에
# 한 번 판정할 뿐이라 실행 중의 상태 변화를 알 수 없고, 스크립트 파일을 만들어
# 실행하거나 별칭·환경변수로 우회하는 경로는 원리상 막지 못한다.
# 진짜 방어선은 .githooks/pre-push(로컬)와 GitHub ruleset(서버)이다.
# -----------------------------------------------------------------------------
#
# 주의: 이 파일은 UTF-8 BOM으로 저장해야 한다. Windows PowerShell 5.1은 BOM이 없으면
# 스크립트를 ANSI 코드페이지로 읽어 한글이 깨진다.
#
# 테스트: powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/guard-main.tests.ps1

$ErrorActionPreference = 'Stop'

# Claude Code는 훅의 stdout을 UTF-8로 읽는다. 콘솔 기본 인코딩(cp949)으로 내보내면 한글이 깨진다.
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$script:WriteOps = @('commit', 'push', 'merge', 'rebase', 'revert', 'cherry-pick', 'reset', 'am', 'apply')

# 값을 인자로 하나 더 먹는 git 전역 옵션
$script:ValueOptions = @('-C', '-c', '--git-dir', '--work-tree', '--exec-path', '--namespace')

# 인자로 받은 문자열을 다시 명령으로 실행하는 래퍼
$script:Wrappers = '^(cmd|sh|bash|dash|zsh|powershell|pwsh)$'

$script:Quotes = @([char]0x22, [char]0x27)
$script:Breaks = @(';', '|', '&', '(', ')', [char]10, [char]13)

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

# 명령 문자열을 세그먼트(; | & 개행·서브셸로 나뉜 단위)의 토큰 배열로 쪼갠다.
# 따옴표는 토큰 경계로만 쓰이고 값에는 남지 않는다 - "main" 과 main 이 같은 토큰이 된다.
function ConvertTo-Segments([string]$text) {
    $segments = @()
    $tokens = @()
    $current = ''
    $hasCurrent = $false
    $quote = ''

    for ($i = 0; $i -lt $text.Length; $i++) {
        $ch = $text[$i]

        if ($quote -ne '') {
            if ($ch -eq $quote) { $quote = '' } else { $current += $ch }
            continue
        }

        if ($script:Quotes -contains $ch) {
            $quote = [string]$ch
            $hasCurrent = $true
            continue
        }

        if ($script:Breaks -contains $ch) {
            if ($hasCurrent) { $tokens += $current; $current = ''; $hasCurrent = $false }
            if ($tokens.Count -gt 0) { $segments += , $tokens; $tokens = @() }
            continue
        }

        if ($ch -eq ' ' -or $ch -eq [char]9) {
            if ($hasCurrent) { $tokens += $current; $current = ''; $hasCurrent = $false }
            continue
        }

        $current += $ch
        $hasCurrent = $true
    }

    if ($hasCurrent) { $tokens += $current }
    if ($tokens.Count -gt 0) { $segments += , $tokens }

    return , $segments
}

# cmd /c "..." 처럼 문자열을 다시 실행하는 래퍼는 그 안을 명령으로 펼쳐서 본다.
function Expand-Wrappers($segments) {
    $out = @()
    foreach ($tokens in $segments) {
        $expanded = $false
        if ($tokens.Count -ge 2) {
            $head = ([System.IO.Path]::GetFileName([string]$tokens[0])) -replace '\.exe$', ''
            if ($head -match $script:Wrappers) {
                foreach ($inner in (ConvertTo-Segments ([string]$tokens[-1]))) { $out += , $inner }
                $expanded = $true
            }
        }
        if (-not $expanded) { $out += , $tokens }
    }
    return , $out
}

# 세그먼트가 git 호출이면 하위 명령과 그 인자를 돌려준다. 아니면 $null.
# 경로로 부르든(/usr/bin/git) 확장자를 붙이든(git.exe) 같게 본다.
function Get-GitSubcommand($tokens) {
    if ($null -eq $tokens -or $tokens.Count -lt 2) { return $null }

    $exe = ([System.IO.Path]::GetFileName([string]$tokens[0])) -replace '\.exe$', ''
    if ($exe -ne 'git') { return $null }

    $i = 1
    while ($i -lt $tokens.Count) {
        $t = [string]$tokens[$i]
        if ($script:ValueOptions -contains $t) { $i += 2; continue }
        if ($t.StartsWith('-')) { $i += 1; continue }
        break
    }
    if ($i -ge $tokens.Count) { return $null }

    $rest = @()
    if ($i + 1 -lt $tokens.Count) { $rest = $tokens[($i + 1)..($tokens.Count - 1)] }

    return @{ Name = ([string]$tokens[$i]).ToLowerInvariant(); Args = $rest }
}

function Test-GitWrite($tokens) {
    $sub = Get-GitSubcommand $tokens
    if ($null -eq $sub) { return $false }
    return ($script:WriteOps -contains $sub.Name)
}

function Test-SwitchToMain($tokens) {
    $sub = Get-GitSubcommand $tokens
    if ($null -eq $sub) { return $false }
    if ($sub.Name -ne 'switch' -and $sub.Name -ne 'checkout') { return $false }

    foreach ($a in $sub.Args) {
        $arg = [string]$a
        if ($arg.StartsWith('-')) { continue }
        $branch = $arg -replace '^refs/heads/', '' -replace '^heads/', ''
        if ($branch -eq 'main') { return $true }
    }
    return $false
}

# 훅이 어떤 이유로든 깨지면 작업을 막지 않는다. 보호 장치가 개발을 인질로 잡으면 안 된다.
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { Allow }
    $payload = $raw | ConvertFrom-Json

    $root = [string]$env:CLAUDE_PROJECT_DIR
    if ([string]::IsNullOrWhiteSpace($root)) { $root = (Get-Location).Path }

    $tool = [string]$payload.tool_name
    $isShell = ($tool -eq 'Bash' -or $tool -eq 'PowerShell')

    $segments = @()
    if ($isShell) {
        $command = [string]$payload.tool_input.command
        if (-not [string]::IsNullOrWhiteSpace($command)) {
            # @()로 감싸지 않는다. 세그먼트가 하나뿐일 때 배열이 한 겹 더 씌워져
            # 토큰 배열이 통째로 토큰 하나로 보인다.
            $segments = Expand-Wrappers (ConvertTo-Segments $command)
        }
    }

    # 실행 전 브랜치만 보면, 작업 브랜치에서 "main으로 전환 후 커밋"을 한 번에 실행해
    # 훅을 통과할 수 있다(TOCTOU). 그래서 이 검사는 현재 브랜치와 무관하게 먼저 한다.
    # 순서를 본다: 전환이 "먼저" 오고 그 뒤에 쓰기 작업이 있을 때만 위험하다.
    # (커밋한 뒤 main으로 돌아가는 것은 정상적인 작업이다.)
    $switched = $false
    foreach ($tokens in $segments) {
        if (-not $switched) {
            if (Test-SwitchToMain $tokens) { $switched = $true }
            continue
        }
        if (Test-GitWrite $tokens) {
            Deny "이 명령은 main으로 전환한 뒤 git 쓰기 작업을 수행합니다.`nmain에는 직접 커밋할 수 없습니다 - 브랜치에서 작업하고 PR로 올리세요."
        }
    }

    $branch = (& git -C $root rev-parse --abbrev-ref HEAD 2>$null)
    if ($LASTEXITCODE -ne 0 -or $branch -ne 'main') { Allow }

    if ($tool -in @('Edit', 'Write', 'MultiEdit', 'NotebookEdit')) {
        # NotebookEdit은 file_path 대신 notebook_path를 줄 수 있다
        $target = [string]$payload.tool_input.file_path
        if ([string]::IsNullOrWhiteSpace($target)) { $target = [string]$payload.tool_input.notebook_path }
        if ([string]::IsNullOrWhiteSpace($target)) { Allow }

        # 문자열 비교만 하면 .claude/../src/X.java 가 .claude/* 예외를 타고 빠져나간다.
        # 실제 경로로 정규화해 '..'를 해소한 뒤 판정한다.
        $rootFull = [System.IO.Path]::GetFullPath($root)
        $targetFull = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($rootFull, $target))

        $rootPrefix = $rootFull.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar
        if (-not $targetFull.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            # 이 저장소 밖의 파일은 우리 규칙이 미치는 범위가 아니다
            Allow
        }

        $relative = $targetFull.Substring($rootPrefix.Length).Replace('\', '/')

        # main에서도 허용: 문서와 Claude 설정
        if ($relative -like '.claude/*') { Allow }
        if ($relative -like '*.md') { Allow }

        Deny "$branchHint`n(차단된 대상: $relative)"
    }

    if ($isShell) {
        foreach ($tokens in $segments) {
            $sub = Get-GitSubcommand $tokens
            if ($null -ne $sub -and ($script:WriteOps -contains $sub.Name)) {
                Deny "$branchHint`n(차단된 명령: git $($sub.Name))"
            }
        }
    }
}
catch {
    Allow
}

Allow
