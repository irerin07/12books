# guard-main.ps1 결정 테스트
#
# 훅을 실제 프로세스로 실행해 allow/deny 판정을 확인한다.
# 임시 git 저장소 두 개(main / 작업 브랜치)를 만들어 CLAUDE_PROJECT_DIR로 가리킨다.
#
# 실행: powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/guard-main.tests.ps1
#
# 주의: 이 파일도 UTF-8 BOM으로 저장해야 한다 (PowerShell 5.1).

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

$hook = Join-Path $PSScriptRoot 'guard-main.ps1'
$tmpRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("guard-main-tests-" + [Guid]::NewGuid().ToString('N'))

function New-TestRepo([string]$name, [string]$branch) {
    $path = Join-Path $tmpRoot $name
    New-Item -ItemType Directory -Path $path -Force | Out-Null
    & git -C $path init --quiet --initial-branch=main
    & git -C $path -c user.email=t@t -c user.name=t commit --quiet --allow-empty -m init
    if ($branch -ne 'main') { & git -C $path switch --quiet -c $branch }
    return $path
}

# 훅을 실행해 'allow' 또는 'deny'를 돌려준다
function Invoke-Hook([string]$repo, [hashtable]$payload) {
    $json = $payload | ConvertTo-Json -Depth 5 -Compress
    $env:CLAUDE_PROJECT_DIR = $repo
    $out = $json | & powershell -NoProfile -ExecutionPolicy Bypass -File $hook
    if ([string]::IsNullOrWhiteSpace($out)) { return 'allow' }
    return ($out | ConvertFrom-Json).hookSpecificOutput.permissionDecision
}

function Edit([string]$path) { @{ tool_name = 'Write'; tool_input = @{ file_path = $path } } }
function Notebook([string]$path) { @{ tool_name = 'NotebookEdit'; tool_input = @{ notebook_path = $path } } }
function Run([string]$command) { @{ tool_name = 'Bash'; tool_input = @{ command = $command } } }

try {
    $mainRepo = New-TestRepo 'on-main' 'main'
    $featRepo = New-TestRepo 'on-feat' 'feat/x'
    $outside = Join-Path $tmpRoot 'somewhere-else'
    New-Item -ItemType Directory -Path $outside -Force | Out-Null

    $cases = @(
        # --- 회귀 방지: 이미 통과하던 것들 ---
        @{ n = 'main + src 상대경로 수정';        r = $mainRepo; p = (Edit 'src/main/java/X.java');                          e = 'deny' }
        @{ n = 'main + src 절대경로 수정';        r = $mainRepo; p = (Edit (Join-Path $mainRepo 'src/main/java/X.java'));    e = 'deny' }
        @{ n = 'main + plan.md 수정';             r = $mainRepo; p = (Edit 'plan.md');                                       e = 'allow' }
        @{ n = 'main + .claude 설정 수정';        r = $mainRepo; p = (Edit '.claude/settings.json');                         e = 'allow' }
        @{ n = 'main + git commit';               r = $mainRepo; p = (Run 'git add -A && git commit -m test');               e = 'deny' }
        @{ n = 'main + git push';                 r = $mainRepo; p = (Run 'git push origin main');                           e = 'deny' }
        @{ n = 'main + git status';               r = $mainRepo; p = (Run 'git status -sb');                                 e = 'allow' }
        @{ n = 'main + gradlew';                  r = $mainRepo; p = (Run './gradlew build');                                e = 'allow' }
        @{ n = '작업브랜치 + src 수정';           r = $featRepo; p = (Edit 'src/main/java/X.java');                          e = 'allow' }
        @{ n = '작업브랜치 + git commit';         r = $featRepo; p = (Run 'git commit -m test');                             e = 'allow' }

        # --- 리뷰 2: 명령 정규식 우회 ---
        @{ n = 'main + 앞 공백 git commit';       r = $mainRepo; p = (Run '  git commit -m x');                              e = 'deny' }
        @{ n = 'main + -C "공백 포함 경로"';      r = $mainRepo; p = (Run 'git -C "path with space" commit -m x');           e = 'deny' }
        @{ n = 'main + cmd /c 래퍼';              r = $mainRepo; p = (Run 'cmd /c "git commit -m x"');                       e = 'deny' }
        @{ n = 'main + 개행으로 이어진 명령';     r = $mainRepo; p = (Run "git add -A`ngit commit -m x");                    e = 'deny' }
        @{ n = 'main + git log --grep=commit';    r = $mainRepo; p = (Run 'git log --grep=commit');                          e = 'allow' }
        @{ n = 'main + git switch -c (탈출구)';   r = $mainRepo; p = (Run 'git switch -c feat/new');                         e = 'allow' }

        # --- 리뷰 3: 경로 정규화 ---
        @{ n = 'main + .claude/../src 우회';      r = $mainRepo; p = (Edit '.claude/../src/main/java/X.java');               e = 'deny' }
        @{ n = 'main + 프로젝트 밖 파일';         r = $mainRepo; p = (Edit (Join-Path $outside 'other.java'));               e = 'allow' }

        # --- 리뷰 4: NotebookEdit의 notebook_path ---
        @{ n = 'main + NotebookEdit';             r = $mainRepo; p = (Notebook 'src/analysis.ipynb');                        e = 'deny' }

        # --- 리뷰 1: TOCTOU (실행 전 브랜치만 보는 문제) ---
        @{ n = '작업브랜치 + main 전환 후 commit'; r = $featRepo; p = (Run 'git switch main && git commit -m x');             e = 'deny' }
    )

    $failed = 0
    foreach ($c in $cases) {
        $actual = Invoke-Hook $c.r $c.p
        if ($actual -eq $c.e) {
            Write-Host ("  PASS  {0}" -f $c.n)
        }
        else {
            Write-Host ("  FAIL  {0}  (기대: {1}, 실제: {2})" -f $c.n, $c.e, $actual)
            $failed++
        }
    }

    Write-Host ""
    Write-Host ("{0}개 중 {1}개 실패" -f $cases.Count, $failed)
    if ($failed -gt 0) { exit 1 }
    exit 0
}
finally {
    Remove-Item -Recurse -Force $tmpRoot -ErrorAction SilentlyContinue
}
