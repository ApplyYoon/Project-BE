---
description: 작업한 브랜치를 GitHub에 올리고 PR을 만듭니다 (체크리스트까지 자동으로 채움)
---

## 1. 준비 상태 확인

```bash
git symbolic-ref --short -q HEAD && git status --short && git log origin/develop..HEAD --oneline
```

- `main` / `develop` 브랜치면 중단하고 `/start` 를 안내합니다.
- **PR 의 대상(base)은 `main` 이 아니라 `develop` 입니다.**
  (`develop → main` PR 은 배포 시점에 사람이 직접 엽니다. AI 가 먼저 만들지 마세요.)
- 커밋 안 된 변경이 남아 있으면 `/save` 를 먼저 하자고 안내합니다.
- 커밋이 하나도 없으면 "아직 저장한 작업이 없어요" 라고 알립니다.

## 2. 체크리스트를 **직접 검증**한다

사용자에게 "체크리스트 확인하셨나요?" 라고 묻지 마세요. **당신이 실제로 돌려서 확인합니다.**

```bash
# 정리 검사 + 빌드 + 테스트 — BUILD SUCCESSFUL 이어야 합니다 (Windows 는 .\gradlew.bat)
./gradlew spotlessCheck build

# 디버깅 흔적 — 새로 추가한 줄에 남아 있으면 지웁니다
git diff origin/develop..HEAD | grep -nE '^\+.*(console\.log|print\(|System\.out|debugger)'

# .env 미포함 — 결과가 비어 있어야 합니다
git diff --name-only origin/develop..HEAD | grep -E '(^|/)\.env' | grep -v '\.env\.example$'

# 비밀값 — 결과가 비어 있어야 합니다 (OpenAI 키, 비밀번호가 든 DB 주소)
git diff origin/develop..HEAD | grep -nE 'sk-[A-Za-z0-9]|postgres(ql)?://[^ ]*:[^ ]*@'

# 커밋 컨벤션 — 모두 "타입: 설명" 형식이어야 합니다
git log origin/develop..HEAD --format=%s
```

새 API 를 만들었다면 테스트 파일도 diff 에 들어 있는지, 새 환경변수가 생겼다면 `.env.example` 에도 있는지 확인합니다.

- **실패한 게 있으면 PR을 올리지 말고 먼저 고칩니다.**
- 고친 뒤에는 `/save` 로 다시 커밋하고 이 단계를 처음부터 다시 합니다.

## 3. 푸시

```bash
git push -u origin <현재 브랜치>
```

## 4. PR 본문 작성 — 템플릿을 **실제 내용으로** 채운다

`.github/PULL_REQUEST_TEMPLATE.md` 를 읽고 다음과 같이 채웁니다.

- **무엇을 했나요 / 왜 이렇게 했나요** — `git log origin/develop..HEAD` 와 `git diff origin/develop..HEAD` 를 읽고 직접 씁니다.
- **바뀐 파일** — `git diff --name-only origin/develop..HEAD` 결과에 각각 한 줄 설명을 붙입니다.
- **✅ 자동 확인** — 2번에서 **실제로 통과한 항목만** `[x]` 로 바꿉니다.
  안 돌려봤거나 실패한 항목은 `[ ]` 로 남기고, 본문에 왜 그런지 한 줄 적습니다.
  **돌려보지 않은 것을 체크하지 마세요. 그게 가장 나쁜 행동입니다.**
- **👀 사람이 확인** — API 를 실제로 불러 보기, H2 콘솔에서 테이블 확인하기 같은 항목입니다.
  당신이 이 대화에서 서버를 켜서 **직접 불러 본 항목은** `[x]` 로 채우고 무엇을 불러 봤는지 적습니다.
  안 해 본 항목은 `[ ]` 로 두고, PR을 올린 뒤 사용자에게 안내하세요.
  > 서버를 켜서 직접 불러 보고 PR 페이지에서 나머지 체크박스를 눌러주세요.
  단, 사용자가 이 대화에서 "해봤다"고 말했다면 그 항목만 `[x]` 로 채워도 됩니다.
- **요청·응답 예시** — API 를 만들었으면 예시를 붙입니다. 실제로 불러 본 게 아니면 "예상 응답" 이라고 적습니다.
- **관련 이슈** — 이슈 번호를 알면 `Closes #12`, 없으면 그 줄을 지웁니다.
- **리뷰어에게** — 자신 없는 부분이 있으면 솔직히 적습니다.
- 본문 마지막 줄: `🤖 Generated with [Claude Code](https://claude.com/claude-code)`

## 5. PR 만들기

`gh` CLI 또는 GitHub MCP 중 되는 것을 씁니다. 제목은 대표 커밋과 같은 형식 — `feat: 날짜별 공부 계획 목록 API 추가`

```bash
gh pr create --base develop --title "feat: ..." --body "$(cat <<'BODY'
...채운 템플릿...
BODY
)"
```

`gh` 가 없으면 GitHub MCP 를 쓰고, 둘 다 없으면 푸시까지만 하고
"GitHub 웹에서 PR 버튼을 눌러주세요" 라며 브랜치 비교 링크를 알려주세요.

## 6. 리뷰어

리뷰는 **선택**입니다. 사용자에게 한 번만 물어보세요.

> "리뷰받고 싶은 팀원 있어요? 없으면 건너뛸게요. (팀원 GitHub 아이디)"

아이디를 말하면 `gh pr edit <번호> --add-reviewer <아이디>`, 없다고 하면 그대로 넘어갑니다.

## 7. 보고 — 반드시 링크와 함께

> PR 올렸어요 👉 https://github.com/.../pull/7
>
> 바꾼 것: 계획 목록 API, 날짜 형식 검사, docs/API.md 예시
> 자동 확인: 빌드·테스트 ✅ .env 없음 ✅ 비밀값 없음 ✅ 커밋 형식 ✅ (체크리스트에 반영했어요)
> 남은 것: 다른 사용자의 데이터가 안 보이는지 확인 → PR 페이지에서 체크해 주세요
>
> 확인이 끝나면 GitHub 웹에서 직접 Merge 버튼을 눌러주세요. (리뷰를 부탁했다면 답을 받은 뒤에)

**merge 는 절대 직접 하지 마세요.**
