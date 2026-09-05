---
description: PR에 달린 코드 리뷰를 읽고 반영한 뒤 다시 푸시한다
argument-hint: [PR 번호 — 생략하면 현재 브랜치의 PR]
---

PR의 코드 리뷰를 반영한다: **$ARGUMENTS**

> **리뷰가 submit되어 있어야 한다.** 작성 중인 PENDING 리뷰는 API로 보이지 않고,
> 답글·resolve도 submit된 스레드에만 가능하다. 조회 결과가 비어 있는데 사용자가 리뷰를
> 남겼다고 하면, GitHub에서 **Submit review**를 눌러달라고 요청한다.

## 1. 리뷰 스레드 수집

PR 번호가 없으면 현재 브랜치의 PR을 쓴다.

스레드의 해결 여부(`isResolved`)는 REST에 없다. **GraphQL `reviewThreads`로 조회한다** —
해결 상태·경로·라인·스레드 내 코멘트를 한 번에 받는다.

```
gh api graphql -f query='
  query($owner:String!, $name:String!, $number:Int!) {
    repository(owner:$owner, name:$name) {
      pullRequest(number:$number) {
        reviewThreads(first:100) {
          nodes {
            id isResolved isOutdated path line
            comments(first:20) { nodes { author { login } body } }
          }
        }
      }
    }
  }' -F owner=irerin07 -F name=12books -F number=<번호>
```

- **`isResolved: false`인 스레드만** 작업 대상이다.
- `isOutdated: true`는 그 사이 코드가 바뀐 스레드다. 지적이 아직 유효한지 먼저 확인한다.
- PR 본문에 달린 일반 리뷰 코멘트는 `gh pr view <번호> --json reviews`로 따로 본다.

추린 목록을 사용자에게 먼저 보여준다 — 몇 건이고 각각 무엇인지.
**리뷰 코멘트가 가리키는 코드를 실제로 열어서 읽는다.** 코멘트 문구만 보고 고치지 않는다.

## 2. 판단

각 지적을 셋 중 하나로 분류한다:

- **반영한다** — 타당한 지적. 3단계로.
- **다르게 반영한다** — 문제는 맞지만 더 나은 해법이 있다. 무엇을 왜 다르게 했는지 답글에 쓴다.
- **반영하지 않는다** — 근거를 들어 설명한다. 사용자가 다시 요구하면 그때는 그대로 따른다.

임의로 범위를 넓히지 않는다. 지적받지 않은 부분을 "겸사겸사" 고치지 않는다.

## 3. 수정 (TDD)

- **버그 지적** — 그 버그를 재현하는 **실패하는 테스트를 먼저 쓰고 실패를 확인한 뒤** 고친다.
  테스트 없이 프로덕션 코드부터 고치지 않는다.
- **설계·가독성 지적** — 기존 테스트가 초록인 것을 확인하고 리팩터링한 뒤 다시 초록을 확인한다.
- **누락된 케이스 지적** — 그 케이스의 테스트를 추가한다(당연히 처음엔 빨갛게).

## 4. 검증 · 푸시

```
.\gradlew.bat build
```

초록불이어야 push한다. 커밋 메시지는 한국어, **트레일러 없이**. 무엇을 왜 고쳤는지 쓴다.
리뷰 반영은 보통 `fix:` 또는 `refactor:`.

```
git push
```

> push가 `synchronize` 이벤트를 일으켜 **`review-gate`가 자동으로 다시 잠긴다.**
> 즉 이전 승인은 무효가 되고 재승인이 필요하다. 이건 의도된 동작이다.

## 5. 답글 · resolve

각 스레드에 무엇을 어떻게 고쳤는지 답글을 단다 (커밋 해시를 함께).

```
gh api repos/{owner}/{repo}/pulls/<번호>/comments/<코멘트id>/replies -f body='...'
```

그리고 **실제로 반영한 스레드만** resolve한다:

```
gh api graphql -f query='
  mutation($id:ID!) { resolveReviewThread(input:{threadId:$id}) { thread { isResolved } } }
  ' -F id=<threadId>
```

- **반영하지 않기로 한 스레드는 열어둔다.** 근거를 답글로 남기고 판단은 사람에게 맡긴다.
- 스레드를 정리하려고 resolve하지 않는다. resolve는 "고쳤다"는 뜻이다.
- 라인 코멘트가 아닌 일반 리뷰는 `gh pr comment <번호> --body '...'`로 요약 답글.

## 6. 보고

사용자에게: 반영한 항목, 다르게 반영한 항목(과 이유), 반영하지 않아 **열어둔** 항목(과 근거),
CI 상태. 그리고 **재승인이 필요하다**는 사실을 알린다 — 다시 `/approve` 코멘트를 남기면 자동 머지된다.
