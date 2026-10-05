# Project BE

Tmax IT 희망학교 팀 프로젝트의 **백엔드**입니다.
학생·입시생이 자기 기록으로 스스로를 알고, 자기에게 맞는 방법으로 꾸준히 공부하게 돕는 AI 자기관리 앱의 서버를 만듭니다.

| 저장소                                                                  | 무엇                 |
| ----------------------------------------------------------------------- | -------------------- |
| [Project-FE](https://github.com/Tmax-IT-Hope-School/Project-FE)         | 프론트엔드 (React)   |
| **Project-BE** (여기)                                                   | 백엔드               |
| [Project](https://github.com/Tmax-IT-Hope-School/Project)               | FE·BE 공용 토론      |

할 일은 [이슈](https://github.com/Tmax-IT-Hope-School/Project-BE/issues)와
조직 보드 **AI 스터디 비서** 에 있습니다. 처음이라면 `good first issue` 라벨부터 고르세요.

---

## 🧰 기술 스택

| 구분          | 사용 기술                                          |
| ------------- | -------------------------------------------------- |
| 프레임워크    | **FastAPI** (Python)                               |
| 패키지 매니저 | **uv** — `pip install` 은 쓰지 않습니다            |
| DB            | **Neon (PostgreSQL)** — SQL 직접 작성 (psycopg)    |
| AI            | OpenAI GPT API — 서버에서만 호출                   |
| 배포          | 무료 서비스 예정 — 미정 (이슈 #8)                  |

린트·테스트 도구 같은 세부는 서버 뼈대(이슈 #4)에서 정합니다.

---

## 🚀 처음 세팅

### 0. 준비물

| 필요한 것 | 확인 명령       | 왜                                       |
| --------- | --------------- | ---------------------------------------- |
| Git       | `git --version` | 필수                                     |
| uv        | `uv --version`  | Python 과 라이브러리를 설치·실행합니다   |
| gh        | `gh --version`  | PR 올리기, GitHub MCP 로그인             |
| jq        | `jq --version`  | Claude Code 안전장치 훅이 씁니다         |
| Node.js   | `node -v`       | GitHub MCP 연결 스크립트만 씁니다 (선택) |

> uv 설치: Mac `brew install uv` / Windows `winget install --id=astral-sh.uv -e`
> Python 은 따로 설치하지 않아도 됩니다. uv 가 프로젝트에 맞는 버전을 알아서 받습니다.

### 1. 코드 받아오기

```bash
git clone https://github.com/Tmax-IT-Hope-School/Project-BE.git
cd Project-BE
```

### 2. git 훅 켜기 — 꼭 하세요

```bash
git config core.hooksPath .githooks
```

이 저장소는 GitHub 브랜치 보호를 켤 수 없어서, **git 훅이 실수를 막는 유일한 장치**입니다.
켜고 나면 아래가 터미널에서도 막힙니다.

- `main` / `develop` 에 직접 커밋·푸시
- `.env` (비밀값 파일) 커밋
- `타입: 설명` 형식이 아닌 커밋 메시지
- 강제 푸시, 원격 브랜치 삭제 푸시

### 3. Claude Code 로 나머지 세팅

```bash
claude
```

"MCP 서버를 사용할까요?" 에 **예** → `/setup` 을 치면 GitHub 로그인·`.env`·MCP 연결까지 한 단계씩 확인해 줍니다.

---

## 🤖 Claude Code 로 작업하기

| 명령                          | 하는 일                                                  |
| ----------------------------- | -------------------------------------------------------- |
| `/setup`                      | **최초 1회** — git 훅·GitHub 로그인·.env·MCP 점검        |
| `/start 공부 계획 API 만들기` | **브랜치 이름을 추천해서 물어보고** 새 브랜치를 만듭니다 |
| `/save`                       | 확인하고 **커밋 메시지까지 알아서 써서** 저장            |
| `/pr`                         | 푸시 + PR 생성 (**체크리스트도 직접 검증해서 채움**)     |
| `/sync`                       | 팀원이 올린 최신 코드 가져오기                           |

### 브랜치 전략

```
feat/plan-api   ─┐
fix/date-bug    ─┼─▶  develop  ─▶  main
chore/hooks     ─┘   (개발 통합)       (최종본)
```

- 작업 브랜치는 **항상 `develop` 에서** 땁니다. PR 의 **base 도 `develop`** 입니다.
- `develop → main` 은 기능이 어느 정도 모였을 때 **선생님/팀장이** 엽니다.

### 안전장치 (2겹)

| 층             | 언제                       | 뚫리는 경우                     |
| -------------- | -------------------------- | ------------------------------- |
| Claude Code 훅 | Claude 가 명령을 실행할 때 | 터미널에 직접 칠 때             |
| git 훅         | 누가 치든 커밋·푸시할 때   | 훅을 안 켰을 때, `--no-verify`  |

Claude 가 실행하는 명령은 강제 푸시·`reset --hard`·`--no-verify` 같은 위험한 명령까지 추가로 막습니다.
전체 목록은 [docs/AI-GUIDE.md](./docs/AI-GUIDE.md) 참고.

---

## 🔐 비밀값

- `DATABASE_URL`, `OPENAI_API_KEY` 같은 값은 **`.env` 에만** 둡니다.
- 저장소에는 빈 값의 `.env.example` 만 올립니다.
- `.env` 는 `.gitignore` 와 git 훅이 이중으로 막습니다.
- 실수로 올렸다면 **바로 팀장에게 알리고 키를 새로 발급**하세요. 지우는 것만으로는 기록에서 사라지지 않습니다.

---

## 📁 폴더 구조

```
app/           FastAPI 서버 코드
db/            DB 구조(schema.sql)와 기초 데이터
tests/         테스트
docs/          팀 문서
.githooks/     커밋·푸시 검사 (git 훅)
.claude/       Claude Code 설정·훅·명령어·스킬
```

`app/` 안의 구조와 서버 실행 방법은 서버 뼈대(#4)에서 정해 여기에 추가합니다.

---

## 📚 문서

| 문서                                         | 언제 읽나                      |
| -------------------------------------------- | ------------------------------ |
| [docs/GIT-GUIDE.md](./docs/GIT-GUIDE.md)     | git/GitHub 가 처음일 때        |
| [docs/CONVENTIONS.md](./docs/CONVENTIONS.md) | 브랜치·커밋·PR 규칙 (한 장)    |
| [docs/AI-GUIDE.md](./docs/AI-GUIDE.md)       | Claude Code·안전장치·MCP       |
| [CONTRIBUTING.md](./CONTRIBUTING.md)         | 협업 규칙 요약                 |
| [CLAUDE.md](./CLAUDE.md)                     | AI가 지키는 규칙 (사람도 읽기) |
