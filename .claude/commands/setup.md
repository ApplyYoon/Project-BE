---
description: 처음 한 번 - 준비물·빌드·git 훅·GitHub 로그인·MCP 연결을 한 번에 세팅합니다
---

팀에 새로 합류했거나 컴퓨터를 새로 받은 사람을 위한 최초 세팅입니다.
**한 단계씩 확인하면서** 진행하고, 실패하면 거기서 멈추고 해결한 뒤 다음으로 가세요.

## 1. 준비물 확인

```bash
git --version && java -version && gh --version && jq --version && node -v
```

| 없는 것 | 해결                                                                                               | 왜 필요한가                         |
| ------- | -------------------------------------------------------------------------------------------------- | ----------------------------------- |
| git     | https://git-scm.com                                                                                | 필수                                |
| JDK 21  | Mac: `brew install --cask temurin@21` / Windows: `winget install EclipseAdoptium.Temurin.21.JDK` / 그 밖: https://adoptium.net | 서버를 빌드하고 실행합니다 |
| gh      | Mac: `brew install gh` / Windows: `winget install --id GitHub.cli` / 그 밖: https://cli.github.com | PR 올리기, GitHub MCP 로그인        |
| jq      | Mac: `brew install jq` / Windows: `winget install jqlang.jq`                                       | Claude 안전장치 훅이 씁니다         |
| node    | https://nodejs.org (22 버전)                                                                       | GitHub MCP 연결 스크립트만 씁니다 (선택) |

`java -version` 이 `21` 로 시작해야 합니다. 17 같은 다른 버전만 있으면 2번에서 에러가 납니다.
다른 버전을 지울 필요는 없고, 21 을 **추가로** 설치한 뒤 터미널을 새로 열면 됩니다.
Gradle(빌드 도구)은 설치하지 않습니다. 저장소에 든 `gradlew` 가 알아서 받습니다.

JDK 설치는 컴퓨터 비밀번호를 물어볼 수 있어서 Claude 가 대신 실행하기 어렵습니다.
**사용자가 직접 터미널에서 실행하라고** 안내하세요.

## 2. 빌드 + 테스트 확인

비밀값이 필요한 담당자만 [docs/SECRETS.md](../../docs/SECRETS.md)의
Infisical 설치 → 개인 로그인 → 개발 프로젝트 실행 순서를 추가로 안내합니다.
가입과 값 입력은 사람이 직접 합니다. CLI 설치·로그인 없이도 아래 H2 빌드는 가능해야 합니다.
토큰이나 비밀값을 채팅으로 요청하지 않습니다.

```bash
./gradlew build
```

(Windows 는 `.\gradlew.bat build`)

- **처음 한 번은 몇 분 걸립니다.** Gradle 과 라이브러리를 내려받는 중이라고 알려주세요.
- `BUILD SUCCESSFUL` 이 나오면 환경은 정상입니다. DB 는 따로 설치하지 않습니다.
- `Cannot find a Java installation ... languageVersion=21` 이 나오면 JDK 21 이 없는 것입니다. 1번으로 돌아가세요.

## 3. git 훅 켜기 — **가장 중요**

이 저장소는 GitHub 브랜치 보호를 켤 수 없어서, git 훅이 main·develop 을 지키는 유일한 장치입니다.

```bash
git config core.hooksPath .githooks
git config core.hooksPath          # .githooks 가 나오면 성공
```

Mac·Linux 에서 훅 파일에 실행 권한이 없다는 에러가 나면:

```bash
chmod +x .githooks/pre-commit .githooks/commit-msg .githooks/pre-push
```

## 4. git 사용자 정보 확인

```bash
git config user.name && git config user.email
```

비어 있으면 사용자에게 물어보고 설정하세요.

```bash
git config --global user.name "본인 이름"
git config --global user.email "GitHub에 등록한 이메일"
```

> 이메일이 GitHub 계정과 다르면 커밋에 본인 프로필이 안 뜹니다.

## 5. GitHub 로그인 (gh CLI)

`/pr` 로 PR 을 올릴 때와 GitHub MCP 를 연결할 때 둘 다 이 로그인이 필요합니다.

```bash
gh auth status
```

`not logged in` 이라고 나오면, `gh auth login` 은 질문에 하나씩 답하며 진행돼서 Claude 가 대신 실행할 수 없습니다.
**새 터미널 창을 열어 직접 실행하라고** 안내하세요.

```bash
gh auth login
```

질문이 나오면 `GitHub.com` → `HTTPS` → `Y` (git push 로그인도 같이 설정) → `Login with a web browser` 를 고르고,
화면에 나온 8자리 코드를 브라우저에 입력하면 끝입니다. 끝나면 `gh auth status` 로 다시 확인하세요.

## 6. MCP 연결 (GitHub)

이 저장소에는 `.mcp.json` 이 들어 있어서, `claude` 를 실행하면
**"이 프로젝트의 MCP 서버를 사용할까요?"** 라고 물어봅니다. **예**를 누르면 연결됩니다.

물어보지 않았거나 이미 거절했다면 상태를 확인하세요.

```
/mcp
```

- `github` 가 목록에 없다면 → Claude Code 를 껐다 켜고 다시 `/mcp`
- `github` 는 5번 `gh` 로그인만 돼 있으면 **자동으로 연결**됩니다.
  (연결할 때마다 `.claude/scripts/github-mcp-headers.mjs` 가 gh 의 토큰을 꺼내 씁니다. 이 스크립트는 **node** 로 돕니다)
- `Incompatible auth server: does not support dynamic client registration` 으로 실패하면 gh 토큰을 꺼내지 못한 것입니다.
  - `gh auth status` 로 로그인 상태 확인 → 안 돼 있으면 5번으로
  - `node -v` 로 node 가 있는지 확인
  - `claude` 를 **프로젝트 폴더 맨 위**에서 켰는지 확인 (스크립트 경로가 이 폴더 기준입니다)

> ⚠️ MCP 를 연결하면 그 서비스에서 **내 계정 권한 그대로** 동작합니다.
> 공식 주소(`api.githubcopilot.com`)가 맞는지 꼭 확인하고, 출처를 모르는 MCP 서버는 붙이지 마세요.

MCP 없이도 이 프로젝트는 전부 동작합니다. 연결이 안 되면 `gh` CLI 나 GitHub 웹을 쓰면 됩니다.

## 7. 마무리 보고

> 세팅 끝났어요.
> · JDK 21 ✅ · 빌드·테스트 ✅ · git 훅 ✅ · git 사용자 정보 ✅ · GitHub 로그인(gh) ✅ · MCP(github) ✅
>
> 이제 `/run` 으로 서버를 켜보고, `/start <만들고 싶은 것>` 으로 첫 작업을 시작하세요.
