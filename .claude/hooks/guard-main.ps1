# main 브랜치 보호 훅 (PreToolUse)
#
# stdin으로 훅 페이로드 JSON을 받아 stdout으로 결정 JSON을 돌려준다.
#
#   - 한 명령 안에서 main 전환 + git 쓰기 작업     -> deny (현재 브랜치와 무관하게)
#   - main에서 소스/설정 파일 수정                 -> deny
#   - main에서 git commit / push / merge / rebase  -> deny
#
# 계획 문서(*.md)와 .claude/ 설정은 main에서도 손볼 수 있게 허용한다.
#
# --- 이 훅의 한계 ------------------------------------------------------------
# 이건 에이전트를 위한 과속방지턱이지 보안 경계가 아니다. 훅은 도구 호출 "전"에
# 한 번 판정할 뿐이라 실행 중의 상태 변화를 알 수 없고, 스크립트 파일을 만들어
# 실행하는 식의 우회는 원리상 막지 못한다.
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

# git 실행 파일. git.exe 로 부르면 빠져나가던 것을 막는다.
$gitExe = '\bgit(?:\.exe)?\b'

# git 쓰기 작업. 토큰을 하나씩 먹으며 훑으므로 앞 공백, 따옴표에 공백이 든 인자
# (git -C "path name" commit), cmd /c - powershell -Command 래퍼, 개행, 그리고
# -C / -c 같은 전역 옵션을 전부 통과시키지 않는다.
# ; & | 를 넘지 않으므로 `git log ... | grep commit` 같은 건 오탐하지 않는다.
# switch/checkout/branch는 일부러 뺐다 - main에서 이걸 막으면 탈출구가 사라진다.
$gitWriteOp = "(?is)$gitExe(?:\s+[^\s;&|]+)*\s+(commit|push|merge|rebase|revert|cherry-pick|reset)\b"

# 한 명령 안에서 main으로 갈아타는 부분.
# git 과 switch 사이의 전역 옵션(-C . / -c key=value)은 통과시키되, main 은 switch/checkout의
# "인자"로 오는 형태만 본다 - 사이에 무엇이든 낄 수 있게 두면 git 예시가 섞인 긴 텍스트를
# 통째로 오탐한다.
$switchToMain = "(?is)$gitExe(?:\s+[^\s;&|]+)*?\s+(?:switch|checkout)\s+(?:-{1,2}[^\s]+\s+)*main\b"

# 훅이 어떤 이유로든 깨지면 작업을 막지 않는다. 보호 장치가 개발을 인질로 잡으면 안 된다.
try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { Allow }
    $payload = $raw | ConvertFrom-Json

    $root = [string]$env:CLAUDE_PROJECT_DIR
    if ([string]::IsNullOrWhiteSpace($root)) { $root = (Get-Location).Path }

    $tool = [string]$payload.tool_name
    $isShell = ($tool -eq 'Bash' -or $tool -eq 'PowerShell')
    $command = ''
    if ($isShell) { $command = [string]$payload.tool_input.command }

    # 인용부호 안의 내용은 보통 실행될 명령이 아니라 데이터다(PR 답글 본문, 커밋 메시지 등).
    # 여기를 걷어내지 않으면 git 사용법을 설명하는 문장을 보내는 것만으로 차단된다.
    #
    # 단, cmd /c "..." 처럼 래퍼가 실행하는 문자열은 데이터가 아니라 명령이다.
    # 그건 따옴표만 벗겨 본문을 살려둔 뒤에 나머지 인용부호를 걷어낸다.
    #
    # git "commit" 처럼 명령어 자체를 따옴표로 감싸는 회피는 여전히 통과한다 -
    # 훅은 과속방지턱이지 셸 파서가 아니다.
    $scan = $command
    if ($isShell) {
        $wrapper = '(?is)((?:cmd(?:\.exe)?\s+/[ck]|(?:ba)?sh\s+-c|(?:pwsh|powershell)(?:\.exe)?\s+(?:-[^\s]+\s+)*?-c(?:ommand)?)\s+)'
        $scan = [regex]::Replace($scan, ($wrapper + '"([^"]*)"'), '$1$2')
        $scan = [regex]::Replace($scan, ($wrapper + "'([^']*)'"), '$1$2')

        $scan = [regex]::Replace($scan, '"[^"]*"', '""')
        $scan = [regex]::Replace($scan, "'[^']*'", "''")
    }

    # 실행 전 브랜치만 보면, 작업 브랜치에서 "main으로 전환 후 커밋"을 한 번에 실행해
    # 훅을 통과할 수 있다(TOCTOU). 그래서 이 검사는 현재 브랜치와 무관하게 먼저 한다.
    # 순서를 본다: 전환이 "먼저" 오고 그 뒤에 쓰기 작업이 있을 때만 위험하다.
    # (커밋한 뒤 main으로 돌아가는 것은 정상적인 작업이다.)
    if ($isShell) {
        $switchMatch = [regex]::Match($scan, $switchToMain)
        if ($switchMatch.Success) {
            $afterSwitch = $scan.Substring($switchMatch.Index + $switchMatch.Length)
            if ($afterSwitch -match $gitWriteOp) {
                Deny "이 명령은 main으로 전환한 뒤 git 쓰기 작업을 수행합니다.`nmain에는 직접 커밋할 수 없습니다 - 브랜치에서 작업하고 PR로 올리세요."
            }
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
        if ([string]::IsNullOrWhiteSpace($command)) { Allow }

        if ($scan -match $gitWriteOp) {
            $op = $Matches[1]
            Deny "$branchHint`n(차단된 명령: git $op)"
        }
    }
}
catch {
    Allow
}

Allow
