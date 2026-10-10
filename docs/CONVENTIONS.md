# 컨벤션 치트시트

한 장으로 보는 팀 규칙. 헷갈릴 때 여기만 보세요.

---

## 🌳 브랜치 전략

```
feat/plan-api   ─┐
fix/date-bug    ─┼─▶  develop  ─▶  main
chore/hooks     ─┘   (개발 통합)       (최종본)
```

| 브랜치        | 무엇                          | 직접 커밋 | PR 대상   |
| ------------- | ----------------------------- | --------- | --------- |
| `main`        | 최종본. 안정적으로 도는 코드  | ❌        | —         |
| `develop`     | 개발 통합. 작업물이 모이는 곳 | ❌        | `main`    |
| `feat/...` 등 | 실제로 작업하는 브랜치        | ✅        | `develop` |

- 작업 브랜치는 **항상 `develop` 에서** 땁니다.
- PR 의 **base 는 `develop`** 입니다. (`main` 아님)
- `develop → main` 은 기능이 어느 정도 모였을 때 **사람이** 엽니다.
- `main` 과 `develop` 은 직접 커밋·푸시가 **자동 차단**됩니다. (git 훅을 켜야 합니다 — `/setup`)

---

## 🌿 브랜치 이름

```
타입/영문-설명            feat/plan-api
타입/이슈번호-영문-설명   feat/11-plan-api
```

| 타입       | 언제                  | 예시                        |
| ---------- | --------------------- | --------------------------- |
| `feat`     | 새 기능               | `feat/11-plan-api`          |
| `fix`      | 버그 수정             | `fix/14-session-day`        |
| `refactor` | 동작 그대로 코드 정리 | `refactor/15-rate-function` |
| `docs`     | 문서만                | `docs/5-api-rules`          |
| `chore`    | 설정·훅·배포 등 기타  | `chore/2-team-rules`        |
| `test`     | 테스트만              | `test/15-rate-examples`     |

✅ 소문자, 하이픈(`-`)
❌ 한글, 공백, 대문자, 언더바(`_`)

```bash
git switch develop && git pull origin develop && git switch -c feat/11-plan-api
```

> Claude Code 를 쓰면 `/start <하고 싶은 일>` 만 치면
> **`develop` 에서 따고 이름 후보까지 제시해 줍니다.**

---

## 💾 커밋

```
<타입>: <한국어로 무엇을 했는지>     ← 50자 이내, 마침표 없음

<왜 그렇게 했는지 — 필요할 때만 2~3줄>

Refs: #12
```

타입은 브랜치와 동일 (+ `style`)

### 좋은 예

```
feat: 날짜별 공부 계획 목록 API 추가

날짜 형식이 틀리면 400 을 돌려주고, 계획이 없는 날은
빈 배열을 돌려주도록 했습니다.

Refs: #11
```

```
fix: 자정 넘은 공부 기록이 다음 날로 잡히는 문제 수정
docs: API 규칙 문서에 에러 모양 추가
chore: git 훅에 비밀값 커밋 차단 추가
```

### 나쁜 예

| ❌                                   | 왜 나쁜가            |
| ------------------------------------ | -------------------- |
| `수정`                               | 뭘 수정했는지 모름   |
| `update`                             | 타입도 내용도 없음   |
| `feat: 작업함`                       | 내용이 없음          |
| `feat: 계획 API 만들고 스키마도 고침` | 한 커밋에 여러 일   |
| `feat: 계획 API 를 추가하였습니다.`  | 마침표·존댓말 불필요 |

### 규칙

- **한 커밋 = 한 가지 일.** 성격이 다르면 나눕니다.
- `git add .` 금지 → 바꾼 파일을 명시합니다.
- **API 키·비밀번호는 커밋하지 않습니다.** 설정 파일에는 `${이름}` 자리만 적습니다. (git 훅이 막습니다)
- Claude가 만든 커밋에는 `Co-Authored-By: Claude ...` 트레일러가 붙습니다.

> `/save` 를 쓰면 **커밋 메시지를 자동으로 써줍니다.** 직접 고민하지 않아도 됩니다.

---

## 🔀 PR

- 제목: 커밋과 같은 형식 — `feat: 날짜별 공부 계획 목록 API 추가`
- 본문: 템플릿을 **실제 내용으로** 채우기 (빈칸 금지)
- 이슈 연결: 본문에 `Closes #12`
- **base 브랜치는 `develop`** — `gh pr create --base develop ...`
- 리뷰는 **선택** — 원할 때만 리뷰어 지정. merge 는 PR 을 올린 사람이 직접 확인한 뒤에
- 크기: 파일 10개 / 300줄 이내 목표

---

## 🧩 코드

Java 21 + Spring Boot + Gradle 로 만듭니다. 로컬 DB 는 H2, 원격 DB 는 Neon 으로 옮길 예정입니다. ([CLAUDE.md](../CLAUDE.md) 1장)

| 무엇         | 규칙                                                         | 예                              |
| ------------ | ------------------------------------------------------------ | ------------------------------- |
| 폴더         | 기능 하나 = `domain/<기능>/` 폴더 하나                       | `domain/subject/`               |
| 클래스 이름  | PascalCase + 역할                                            | `SubjectController`, `SubjectService` |
| API 주소     | `/api/v1/` 아래 소문자 kebab-case 복수형                     | `/api/v1/study-sessions`        |
| JSON 필드    | camelCase (FE 타입과 같게)                                   | `targetMinutes`                 |
| DB 컬럼      | snake_case (엔티티 필드를 camelCase 로 쓰면 알아서 바뀜)     | `userId` → `user_id`            |
| 응답         | 엔티티 말고 항상 DTO(record)                                 | `SubjectResponse`               |
| 테스트       | `main` 과 같은 폴더에, 이름 뒤에 `Test`                      | `SubjectControllerTest`         |

커밋 전에: `./gradlew spotlessApply` → `./gradlew spotlessCheck build` (Claude Code 는 `/check`)

> 어느 파일을 어디에 만드는지: [FOLDER-STRUCTURE.md](./FOLDER-STRUCTURE.md)
> 전체 코드 규칙: [CLAUDE.md](../CLAUDE.md) 3장
