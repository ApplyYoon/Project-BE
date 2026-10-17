---
description: 새 작업 시작 - 브랜치 이름을 추천해주고 골라서 작업 브랜치를 만듭니다
argument-hint: [무엇을 만들지 한국어로]
---

사용자가 새 작업을 시작합니다. 하고 싶은 일: **$ARGUMENTS**

## 1. 지금 상태 확인

```bash
git symbolic-ref --short -q HEAD && git status --short
```

- 커밋 안 된 수정이 있으면 **멈추고** 물어봅니다.
  > "저장 안 된 작업이 N개 있어요. `/save` 로 먼저 저장할까요, 아니면 그대로 새 브랜치로 가져갈까요?"

## 2. 브랜치 이름 후보를 **만들어서 물어본다**

사용자는 영어 이름을 짓기 어려워합니다. **당신이 후보를 3개 만들어서 고르게 하세요.**
그냥 하나 정하고 넘어가는 것도, "브랜치 이름 뭐로 할까요?" 라고 빈손으로 묻는 것도 안 됩니다.

### 이름 규칙

```
타입/영문-설명            feat/plan-api
타입/이슈번호-영문-설명   feat/11-plan-api     ← 이슈 번호를 알면 이 형태
```

소문자 + 하이픈만. 한글·공백·대문자·언더바 금지. 2~4단어가 적당합니다.

### 타입 고르기

| 사용자가 이렇게 말하면                       | 타입       |
| -------------------------------------------- | ---------- |
| 만들어줘 / 추가해줘 / 새로                   | `feat`     |
| 안 돼요 / 이상해요 / 에러나요 / 고쳐줘       | `fix`      |
| 정리해줘 / 깔끔하게 / 나눠줘 (동작은 그대로) | `refactor` |
| README / 문서 / 설명                         | `docs`     |
| 설정 / 훅 / 배포 / 패키지                    | `chore`    |
| 테스트만 추가                                | `test`     |

### 자주 쓰는 한국어 → 영어

| 한국어      | 영어         | 한국어      | 영어         |
| ----------- | ------------ | ----------- | ------------ |
| 공부 계획   | `plan`       | 과목        | `subject`    |
| 공부 기록   | `session`    | 체크인      | `checkin`    |
| 시험 점수   | `exam`       | 회고        | `review`     |
| 로그인      | `auth`       | 내 정보     | `me`         |
| 설정        | `settings`   | 온보딩 설문 | `onboarding` |
| 통계        | `stats`      | 리포트      | `report`     |
| 인사이트    | `insight`    | 개선안      | `improvement`|
| 메시지      | `message`    | 알림(푸시)  | `push`       |
| 일기        | `diary`      | 통화        | `call`       |
| DB 구조     | `schema`     | 배포        | `deploy`     |
| 예약 작업   | `cron`       | 안전 브레이크 | `safety`   |

### 후보 만드는 요령

후보 3개는 **서로 다른 각도**로 만듭니다. 비슷한 걸 3개 내놓지 마세요.

- 예: "공부 계획 API 만들래" (이슈 #11)
  1. `feat/11-plan-api` — API 단위로 본 이름
  2. `feat/11-plans-by-date` — 기능(날짜별 조회) 중심으로 본 이름
  3. `feat/11-study-plan` — 공부 계획 기능 전체로 본 이름

- 예: "자정 넘어 공부한 게 다음 날로 잡혀요"
  1. `fix/session-day-boundary`
  2. `fix/midnight-session-date`
  3. `fix/today-total-timezone`

`AskUserQuestion` 으로 물어보세요. (사용자가 직접 입력할 수도 있습니다)

이슈 번호를 이미 알고 있다면 후보에 번호를 넣어 제시하세요. (`feat/11-plan-api`)

## 3. 최신 develop 에서 브랜치 만들기

작업 브랜치는 **`main` 이 아니라 `develop` 에서** 땁니다.
(브랜치 전략: `작업 브랜치 → develop → main`)

```bash
git switch develop
git pull origin develop
git switch -c <고른-이름>
```

`develop` 이 없다는 에러가 나면 원격에서 받아옵니다.

```bash
git fetch origin && git switch -c develop origin/develop
```

그래도 없으면 아직 develop 이 안 만들어진 것입니다. 사용자에게 알리고 멈추세요.
(develop 은 사람이 GitHub 웹에서 만듭니다)

`git pull` 이 실패해도(인터넷/권한) 그대로 진행하되 사용자에게 알려주세요.

## 4. 짧게 보고하고 바로 작업 시작

> `feat/11-plan-api` 브랜치를 만들었어요. 이제 작업 시작할게요.
> 다 되면 `/save` 로 저장하고, `/pr` 로 올리면 됩니다.

그리고 **$ARGUMENTS 작업을 바로 이어서 진행하세요.** 여기서 멈추지 마세요.

작업할 때는 CLAUDE.md 3장(비밀값·DB 규칙)과 4장(개인정보)을 지킵니다.
