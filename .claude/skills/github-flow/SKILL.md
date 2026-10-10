---
name: github-flow
description: git·GitHub 작업 전체 흐름입니다. 브랜치 만들기(이름을 사용자에게 물어봄), 커밋 메시지 자동 작성, 푸시, PR 생성, 충돌 해결을 이 팀 규칙대로 처리합니다. 커밋·브랜치·푸시·PR·머지·이슈·GitHub MCP·gh CLI 관련 작업이면 항상 읽으세요.
---

# GitHub 작업 흐름

이 저장소는 고등학생 팀 프로젝트입니다. **사고를 막는 것이 속도보다 중요합니다.**
그리고 GitHub 브랜치 보호를 켤 수 없는 저장소라, **훅이 유일한 안전장치**입니다.

## 브랜치 전략

```
feat/plan-api   ─┐
fix/date-bug    ─┼─▶  develop  ─▶  main
chore/hooks     ─┘   (개발 통합)       (최종본)
```

| 브랜치        | 무엇                          | 직접 커밋 |
| ------------- | ----------------------------- | --------- |
| `main`        | 최종본. 안정적으로 도는 코드  | ❌ 금지   |
| `develop`     | 개발 통합. 작업물이 모이는 곳 | ❌ 금지   |
| `feat/...` 등 | 실제로 작업하는 브랜치        | ✅        |

- 작업 브랜치는 **항상 `develop` 에서** 땁니다. (`main` 이 아닙니다)
- 작업이 끝나면 **`develop` 으로 PR** 을 올립니다.
- `develop → main` PR 은 **기능이 어느 정도 모였을 때 사람이** 엽니다. AI 가 먼저 만들지 않습니다.
- `main` 과 `develop` 은 훅이 직접 커밋·푸시를 **자동으로 차단**합니다.

---

## 기본 흐름 (요청 하나 = 이 9단계)

```
확인 → 이슈 → 브랜치 → 작업 → 검사 → 커밋 → 푸시 → PR → 보고
```

### 1. 확인

```bash
git symbolic-ref --short -q HEAD && git status --short && git config core.hooksPath
```

- 남의 작업물로 보이는 수정이 섞여 있으면 **멈추고 물어봅니다.**
- 지금 `main` 이나 `develop` 위에 있다면 **코드를 고치기 전에** 3번으로 갑니다.
- `core.hooksPath` 가 `.githooks` 가 아니면 `/setup` 을 안내합니다.

### 2. 이슈 (있으면 좋고, 없어도 됨)

관련 이슈가 있으면 번호를 기억합니다. 없으면 만들지 물어봅니다.

### 3. 브랜치 — **`develop` 에서 따고, 이름은 반드시 물어본다**

코드를 고치기 **전에** 브랜치를 만듭니다. 절대 예외 없습니다.
기준은 `main` 이 아니라 **`develop`** 입니다.

이름 후보 2~3개를 만들어 `AskUserQuestion` 으로 고르게 하세요. 혼자 정하지 마세요.

```
타입/영문-설명        예: feat/plan-api
타입/이슈번호-설명    예: feat/11-plan-api
```

타입: `feat` `fix` `refactor` `docs` `chore` `test`
소문자·하이픈만. 한글·공백·대문자 금지.

```bash
git switch develop && git pull origin develop && git switch -c feat/11-plan-api
```

`develop` 이 없다는 에러가 나면: `git fetch origin && git switch -c develop origin/develop`

### 4. 작업

한 브랜치 = 한 가지 목적. 하다가 다른 것도 고치고 싶어지면 그건 다음 브랜치로 미룹니다.
비밀값은 코드에 쓰지 않고, 새 API 를 만들면 테스트도 같이 씁니다.

### 5. 검사 — 통과 못 하면 커밋 금지

```bash
./gradlew spotlessApply          # 코드 모양 자동 정리
./gradlew spotlessCheck build    # 정리 검사 + 빌드 + 테스트
```

Windows 는 `.\gradlew.bat`. 그 밖의 확인은 CLAUDE.md 5장을 따릅니다.

### 6. 커밋 — 메시지는 당신이 씁니다

사용자에게 "커밋 메시지 뭐로 할까요?" 라고 묻지 마세요. `git diff` 를 읽고 **직접 쓰세요.**

```
<타입>: <한국어로 무엇을 했는지>   ← 50자 이내, 마침표 없음

<왜 그렇게 했는지 — 필요할 때만 2~3줄>

Refs: #12
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

| 좋은 예                                        | 나쁜 예               |
| ---------------------------------------------- | --------------------- |
| `feat: 날짜별 공부 계획 목록 API 추가`         | `수정`                |
| `fix: 자정 넘은 기록이 다음 날로 잡히는 문제 수정` | `update`          |
| `chore: git 훅에 .env 커밋 차단 추가`          | `feat: 이것저것 고침` |

- **한 커밋 = 한 가지 일.** 성격이 다르면 `git add` 를 나눠서 커밋을 쪼갭니다.
- `git add .` 금지. 바꾼 파일을 명시합니다. **`.env` 는 절대 add 하지 않습니다.**

### 7. 푸시

```bash
git push -u origin <브랜치명>
```

### 8. PR — **base 는 `develop`**

```bash
gh pr create --base develop --title "feat: ..." --body "..."
```

- 제목: 커밋 제목과 같은 형식
- 본문: `.github/PULL_REQUEST_TEMPLATE.md` 를 **실제 내용으로** 채웁니다
- 이슈 연결: `Closes #12`
- 리뷰어: 선택. 리뷰받고 싶은지 물어보고, 원할 때만 지정
- 파일 10개 / 300줄 넘으면 나눠 올리자고 제안

### 9. 보고

PR **링크**와 바꾼 것 3줄 요약. merge 는 하지 않습니다.

---

## 절대 혼자 하지 않는 것

먼저 이유를 설명하고 허락을 받으세요. (`.claude/hooks/guard-git.sh` 가 일부는 막아둡니다)

- force push 전부 — `--force`, `--force-with-lease`, `-f`, `git push origin +main`
- `git push --mirror` (원격 브랜치 전멸), `--delete`, `--prune`
- `git reset --hard`, `git clean -f`, `git checkout .`, `git stash drop`
- `git rebase`, `git filter-branch`, `git reflog expire`, `git gc --prune`
- `git remote set-url`, `rm -rf .git`
- `--no-verify`, `core.hooksPath` 변경 (git 훅 끄기)
- `main` 또는 `develop` 에 직접 커밋·푸시
- `develop → main` PR 을 사람 지시 없이 만들기 / merge 하기
- 브랜치 삭제, PR merge / close
- 올라간 커밋 수정 (`commit --amend`)
- 저장소 설정 변경, 협업자 추가/삭제
- 라이브러리 설치·삭제, DB 테이블 삭제

## 충돌(conflict)이 났을 때

혼자 아무거나 고르지 마세요.

1. 어느 파일이 왜 충돌했는지 한국어로 설명합니다.
2. 충돌 부분마다 "내 코드 / 팀원 코드 / 둘 다 필요" 중 무엇인지 **사용자에게 물어봅니다.**
3. `db/schema.sql` 이 충돌했다면 두 사람의 테이블 변경이 둘 다 살아 있는지 특히 꼼꼼히 봅니다.

## GitHub MCP 를 쓸 때

MCP 도구가 있으면 `gh` CLI 대신 써도 됩니다. **규칙은 똑같습니다.**

- 브랜치 이름은 물어보고, 커밋 메시지는 컨벤션대로 직접 씁니다.
- MCP 로 파일을 직접 수정해서 `main` 에 올리는 기능은 쓰지 마세요.
  항상 로컬에서 브랜치를 만들고 → 커밋 → 푸시 → PR 순서를 따릅니다.
