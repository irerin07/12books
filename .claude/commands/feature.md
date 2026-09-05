---
description: 기능 하나를 브랜치·TDD·커밋·PR·자동머지 예약까지 한 번에 진행한다
argument-hint: <무엇을 만들지 — 예: "Phase 1 사용자·JWT 인증">
---

기능을 처음부터 끝까지 진행한다: **$ARGUMENTS**

`CLAUDE.md`의 불변 규칙이 전부 적용된다. 아래 단계를 순서대로, 건너뛰지 말고 실행한다.

## 1. 브랜치

```
git switch main
git pull --ff-only
git switch -c <타입>/<슬러그>
```

타입은 `feat`/`fix`/`chore` 중 내용에 맞는 것. 슬러그는 영문 소문자·하이픈
(예: `feat/phase-1-auth`). 요청이 `plan.md`의 Phase에 해당하면 그 번호를 슬러그에 넣는다.

작업 전에 `plan.md`에서 해당 Phase의 **산출물·기술 상세·완료 기준**을 읽는다. 계획에 이미
적혀 있는 설계 결정을 다시 발명하지 않는다.

## 2. TDD 사이클

작업을 관찰 가능한 동작 단위로 쪼갠 뒤, 각 단위마다:

1. **RED** — 그 동작을 표현하는 가장 작은 테스트를 쓰고 **실행해서 실패를 확인한다.**
   실패 이유가 "기능이 없어서"인지 확인한다. 컴파일 에러·오타로 인한 실패는 RED가 아니다.
   실패를 보지 않고 프로덕션 코드를 쓰지 않는다.
2. **GREEN** — 그 테스트를 통과시키는 **최소한의** 코드만 쓴다. 요청 범위 밖 기능을 얹지 않는다.
3. **REFACTOR** — 지금 작업이 요구할 때만. 초록불을 유지한 채로.

같은 동작을 중복해서 지키는 테스트를 여러 개 만들지 않는다. 하나의 테스트가 여러 단언을
가질 수는 있다 — 그것들이 하나의 관찰 가능한 계약을 함께 서술한다면.

## 3. 전체 초록불

```
.\gradlew.bat build
```

Testcontainers에 Docker가 필요하다. 안 떠 있으면 `docker compose up -d`.

**실패하면 여기서 멈춘다.** 고치고 다시 돌린다. 빨간 상태로 커밋하거나 PR을 만들지 않는다.

## 4. 커밋 · 푸시 · PR

`git status`와 `git diff`로 **실제 변경분만** 스테이징한다. 의도치 않은 파일이 섞였는지 본다.

커밋 메시지는 한국어, 제목 `<타입>: 요약` + 본문(왜 그렇게 했는지).
**트레일러(`Co-Authored-By`, `Claude-Session`, `Generated with`)를 붙이지 않는다.**

```
git push -u origin <브랜치>
gh pr create --fill-first --base main
```

PR 본문은 `.github/pull_request_template.md` 형식을 채운다 — 무엇을/왜, 어떻게 검증했는지,
관련 Phase, 리뷰어가 특히 봐줬으면 하는 지점.

## 5. 자동 머지 예약

```
gh pr merge --auto --squash
```

머지를 **예약만** 한다. `build`와 `review-gate`가 모두 초록이 되는 순간 GitHub이 머지한다.
`review-gate`는 사용자가 PR에 `/approve <head 커밋 SHA>` 코멘트를 남겨야 초록이 되므로,
승인 전에는 대기한다.

## 6. 보고

사용자에게 PR URL과 함께 이것들을 알린다:
- 무엇을 만들었고 어떤 테스트가 그것을 지키는지
- CI 상태 (`gh pr checks`)
- **다음에 사용자가 할 일**: PR을 리뷰하고, 문제없으면 `/approve <head SHA 앞 7자리>` 코멘트를
  남기면 자동 머지됨. 이때 쓸 SHA를 보고에 함께 적어준다

여기서 멈춘다. 승인을 대신하거나 재촉하지 않는다.
