#!/usr/bin/env bash
#
# 승인 지시들을 읽어 현재 head의 게이트 판정을 내린다.
#
#   사용법: <지시 줄들> | review-gate-decide.sh <head SHA>
#   출력:   <verdict>\t<description>\t<bare_approve>
#
# 지시 줄(탭 구분):
#   <ISO8601>  COMMENT  /approve <sha>
#   <ISO8601>  COMMENT  /reject
#   <ISO8601>  REVIEW   APPROVED|CHANGES_REQUESTED  <commit sha>
#
# 워크플로에서 떼어낸 이유는 이 판정 로직을 테스트할 수 있게 하기 위해서다.
# 테스트: bash .github/scripts/review-gate-decide.tests.sh

set -uo pipefail

head_sha="${1:?head SHA가 필요합니다}"
short_sha="${head_sha:0:7}"

verdict=""
detail=""
verdict_ts=""
bare_approve=0

# 지시 하나의 판정을 반영한다.
#
# GitHub 타임스탬프는 초 단위라, 같은 초에 일어난 두 지시의 실제 순서는 알 수 없다.
# 그럴 때 승인으로 덮으면 "사람이 마지막에 거절했는데 승인으로 표시되는" 사고가 난다.
# 그래서 같은 초에서는 거절이 이긴다. 정말 승인하고 싶다면 /approve <SHA>를 한 번 더
# 남기면 되고, 그건 더 나중 초가 되므로 정상적으로 뒤집힌다.
apply() {
	local ts="$1" v="$2" d="$3"

	if [ "$ts" = "$verdict_ts" ] && [ "$verdict" = "failure" ] && [ "$v" = "success" ]; then
		return
	fi

	verdict="$v"
	detail="$d"
	verdict_ts="$ts"
}

while IFS=$'\t' read -r ts kind rest extra; do
	[ -n "${ts:-}" ] || continue

	case "${kind:-}" in
	COMMENT)
		# 따옴표·백틱·굵게 표기를 걷어낸다. 안내 문구를 따옴표째 복사해 붙이는 일이
		# 실제로 있었고, 표기 때문에 사람이 쓴 승인을 놓치면 안 된다.
		line=$(printf '%s' "${rest:-}" | sed -e 's/^[][:space:]"`*'"'"']*//' -e 's/[[:space:]"`*'"'"']*$//')
		cmd=$(printf '%s' "$line" | awk '{print $1}')
		# SHA는 영숫자뿐이다. 뒤에 문장이 이어져 따옴표가 붙어 와도 잘라낸다.
		arg=$(printf '%s' "$line" | awk '{print $2}' | tr -cd '[:alnum:]')

		if [ "$cmd" = "/reject" ]; then
			apply "$ts" failure "변경 요청됨 — 수정 후 /approve $short_sha"
		elif [ "$cmd" = "/approve" ]; then
			if [ -z "$arg" ] || [ ${#arg} -lt 7 ]; then
				# 커밋을 안 적은 승인은 어떤 코드에 대한 것인지 알 수 없다
				bare_approve=1
			elif [ "${head_sha#"$arg"}" != "$head_sha" ]; then
				apply "$ts" success "승인됨 — $arg"
			fi
			# 다른 커밋을 가리키는 승인은 지나간 승인이므로 무시한다
		fi
		;;
	REVIEW)
		# 리뷰는 대상 커밋을 스스로 들고 있다. 현재 head에 대한 것만 본다.
		if [ "${extra:-}" = "$head_sha" ]; then
			if [ "${rest:-}" = "APPROVED" ]; then
				apply "$ts" success "승인됨 — 리뷰 approve"
			else
				apply "$ts" failure "변경 요청됨 — 수정 후 /approve $short_sha"
			fi
		fi
		;;
	esac
done < <(sort)

if [ -z "$verdict" ]; then
	verdict="failure"
	# 따옴표를 두르지 않는다. 이 문구를 그대로 복사해 붙이는 사람이 있고,
	# 따옴표까지 딸려 오면 승인이 인식되지 않는다.
	detail="승인 대기 중 — PR에 다음 코멘트를 남기면 자동 머지됩니다: /approve $short_sha"
fi

printf '%s\t%s\t%s\n' "$verdict" "$detail" "$bare_approve"
