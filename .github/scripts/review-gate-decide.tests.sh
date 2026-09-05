#!/usr/bin/env bash
# review-gate-decide.sh 판정 테스트
#
# 실행: bash .github/scripts/review-gate-decide.tests.sh

set -uo pipefail

script="$(dirname "$0")/review-gate-decide.sh"
head='20ce5cc1111111111111111111111111111111aa'
failed=0

# check <이름> <기대 verdict> <지시 줄들...>
check() {
	local name="$1" want="$2"
	shift 2

	local input=""
	local line
	for line in "$@"; do
		input+="$line"$'\n'
	done

	local got
	got=$(printf '%s' "$input" | bash "$script" "$head" | cut -f1)

	if [ "$got" = "$want" ]; then
		echo "  PASS  $name"
	else
		echo "  FAIL  $name  (기대: $want, 실제: $got)"
		failed=$((failed + 1))
	fi
}

T1='2026-09-05T12:00:00Z'
T2='2026-09-05T12:00:05Z'

check '지시 없음 → 승인 대기' failure

check '현재 head 승인' success \
	"$T1	COMMENT	/approve 20ce5cc"

check '다른 커밋을 가리키는 승인은 무시' failure \
	"$T1	COMMENT	/approve deadbee"

check '커밋 없는 /approve 는 승인 아님' failure \
	"$T1	COMMENT	/approve"

# 안내 문구를 따옴표째 복사해 붙이는 일이 실제로 있었다. 사람이 쓴 승인을
# 표기 때문에 놓치면 안 된다.
check '따옴표로 감싼 승인' success \
	"$T1	COMMENT	\"/approve 20ce5cc\""

check '앞 따옴표만 붙은 승인' success \
	"$T1	COMMENT	\"/approve 20ce5cc"

check '백틱으로 감싼 승인' success \
	"$T1	COMMENT	\`/approve 20ce5cc\`"

check '굵게 표기한 승인' success \
	"$T1	COMMENT	**/approve 20ce5cc**"

check '따옴표로 감싼 거절' failure \
	"$T1	COMMENT	\"/reject\""

check '거절만' failure \
	"$T1	COMMENT	/reject"

check '승인 후 거절' failure \
	"$T1	COMMENT	/approve 20ce5cc" \
	"$T2	COMMENT	/reject"

check '거절 후 재승인' success \
	"$T1	COMMENT	/reject" \
	"$T2	COMMENT	/approve 20ce5cc"

check '리뷰 approve (현재 head)' success \
	"$T1	REVIEW	APPROVED	$head"

check '리뷰 approve (다른 커밋)' failure \
	"$T1	REVIEW	APPROVED	deadbeef1111111111111111111111111111beef"

check '리뷰 changes_requested' failure \
	"$T1	REVIEW	CHANGES_REQUESTED	$head"

# GitHub 타임스탬프는 초 단위다. 같은 초에 승인과 거절이 함께 있으면 실제 순서를
# 알 수 없다. 그럴 때는 안전한 쪽(거절)으로 닫는다.
check '같은 초: 리뷰 승인 + 코멘트 거절 → 거절 우선' failure \
	"$T1	REVIEW	APPROVED	$head" \
	"$T1	COMMENT	/reject"

check '같은 초: 코멘트 승인 + 리뷰 거절 → 거절 우선' failure \
	"$T1	COMMENT	/approve 20ce5cc" \
	"$T1	REVIEW	CHANGES_REQUESTED	$head"

check '같은 초의 거절을 나중 초의 승인이 뒤집는다' success \
	"$T1	REVIEW	APPROVED	$head" \
	"$T1	COMMENT	/reject" \
	"$T2	COMMENT	/approve 20ce5cc"

echo ""
if [ "$failed" -gt 0 ]; then
	echo "$failed개 실패"
	exit 1
fi
echo "전부 통과"
