# guard-question.ps1 결정 테스트
#
# 훅을 실제 프로세스로 실행해 allow/deny 판정을 확인한다. 캐시(한 번 물어본 표시)는
# CLAUDE_GUARD_QUESTION_CACHE로 임시 폴더에 가둔다 - 실제 캐시를 건드리면 테스트를 돌린
# 뒤 진짜 대화에서 물어봐야 할 질문이 조용히 통과한다.
#
# 실행: powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/guard-question.tests.ps1
#
# 주의: 이 파일도 UTF-8 BOM으로 저장해야 한다 (PowerShell 5.1).

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

# 페이로드는 파이프가 아니라 프로세스의 stdin에 UTF-8 바이트로 직접 쓴다.
#
# PowerShell 5.1이 네이티브 프로세스로 파이프할 때 쓰는 인코딩은 $OutputEncoding과 콘솔
# 설정에 따라 기계마다 다르다. 내 기계에서는 통과하고 GitHub 러너에서는 한글 라벨이 통째로
# 깨져 도착했다 - 그러면 테스트가 훅이 아니라 파이프를 검사하는 셈이 된다. 바이트를 직접
# 쓰면 기계와 무관하게 같고, 실제 Claude Code가 stdin에 UTF-8을 쓰는 것과도 같아진다.

$hook = Join-Path $PSScriptRoot 'guard-question.ps1'
$tmpRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("guard-question-tests-" + [Guid]::NewGuid().ToString('N'))

function Invoke-Raw([string]$text) {
    $env:CLAUDE_GUARD_QUESTION_CACHE = $tmpRoot

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = 'powershell'
    $psi.Arguments = "-NoProfile -ExecutionPolicy Bypass -File `"$hook`""
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.StandardOutputEncoding = New-Object System.Text.UTF8Encoding($false)

    $proc = [System.Diagnostics.Process]::Start($psi)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
    $proc.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
    $proc.StandardInput.BaseStream.Flush()
    $proc.StandardInput.Close()
    $out = $proc.StandardOutput.ReadToEnd()
    $proc.WaitForExit()
    return $out
}

function Invoke-Hook([hashtable]$payload) {
    $out = Invoke-Raw ($payload | ConvertTo-Json -Depth 10 -Compress)
    if ([string]::IsNullOrWhiteSpace($out)) { return 'allow' }
    return ($out | ConvertFrom-Json).hookSpecificOutput.permissionDecision
}

function Question([string]$session, [string[]]$labels) {
    $options = @()
    foreach ($label in $labels) { $options += @{ label = $label; description = '설명' } }
    return @{
        session_id = $session
        tool_name  = 'AskUserQuestion'
        tool_input = @{
            questions = @(
                @{ question = '무엇으로 할까요?'; header = '선택'; multiSelect = $false; options = $options }
            )
        }
    }
}

try {
    New-Item -ItemType Directory -Path $tmpRoot -Force | Out-Null

    $cases = @(
        # --- 실제로 있었던 그 질문 ---
        @{ n = '발송자와 전송 방식이 섞인 목록'; p = (Question 's1' @('Resend (추천)', 'AWS SES', 'Gmail SMTP', '아직 정하지 않는다')); e = 'deny' }

        # --- 같은 축만 묻는 목록은 통과 ---
        @{ n = '발송자만';                       p = (Question 's2' @('Resend', 'AWS SES', 'Postmark'));                      e = 'allow' }
        @{ n = '전송 방식만';                    p = (Question 's3' @('SMTP (JavaMailSender)', 'HTTP API'));                  e = 'allow' }
        @{ n = '기술 이름이 없는 목록';          p = (Question 's4' @('지금 만든다', '다음 Phase로 미룬다'));                e = 'allow' }
        @{ n = '보류 선택지는 종류에서 뺀다';    p = (Question 's5' @('SMTP', 'HTTP API', '아직 정하지 않는다'));             e = 'allow' }

        # --- 겹치는 선택지 ---
        @{ n = '한 선택지가 다른 것을 품음';     p = (Question 's6' @('Resend', 'Resend SMTP'));                              e = 'deny' }
        @{ n = '(추천) 표시는 무시하고 비교';    p = (Question 's7' @('Resend (추천)', 'Resend'));                            e = 'deny' }

        # --- 다른 도구와 망가진 입력은 건드리지 않는다 ---
        @{ n = '다른 도구';                      p = @{ session_id = 's8'; tool_name = 'Bash'; tool_input = @{ command = 'git status' } }; e = 'allow' }
        @{ n = '선택지가 없는 질문';             p = (Question 's9' @());                                                     e = 'allow' }
    )

    $failed = 0
    foreach ($c in $cases) {
        $actual = Invoke-Hook $c.p
        if ($actual -eq $c.e) {
            Write-Host ("  PASS  {0}" -f $c.n)
        }
        else {
            Write-Host ("  FAIL  {0}  (기대: {1}, 실제: {2})" -f $c.n, $c.e, $actual)
            $failed++
        }
    }

    # 같은 질문을 다시 보내면 통과한다. 이게 없으면 오탐이 교착이 된다.
    $again = Question 's10' @('Resend', 'Gmail SMTP')
    $first = Invoke-Hook $again
    $second = Invoke-Hook $again
    if ($first -eq 'deny' -and $second -eq 'allow') {
        Write-Host "  PASS  두 번째 요청은 통과한다"
    }
    else {
        Write-Host ("  FAIL  두 번째 요청은 통과한다  (실제: {0} -> {1})" -f $first, $second)
        $failed++
    }

    # 빈 입력에도 죽지 않는다
    $empty = Invoke-Raw ''
    if ([string]::IsNullOrWhiteSpace($empty)) {
        Write-Host "  PASS  빈 입력은 그냥 통과한다"
    }
    else {
        Write-Host ("  FAIL  빈 입력은 그냥 통과한다  (실제: {0})" -f $empty)
        $failed++
    }

    $total = $cases.Count + 2
    Write-Host ""
    Write-Host ("{0}개 중 {1}개 실패" -f $total, $failed)
    if ($failed -gt 0) { exit 1 }
    exit 0
}
finally {
    Remove-Item -Recurse -Force $tmpRoot -ErrorAction SilentlyContinue
}
