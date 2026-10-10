# Project BE

Tmax IT 희망학교 팀 프로젝트의 **백엔드**입니다.
학생·입시생이 자기 기록으로 스스로를 알고, 자기에게 맞는 방법으로 꾸준히 공부하게 돕는 AI 자기관리 앱의 서버를 만듭니다.
**Java 21 + Spring Boot + Gradle** 로 만듭니다.

| 저장소                                                                  | 무엇                 |
| ----------------------------------------------------------------------- | -------------------- |
| [Project-FE](https://github.com/Tmax-IT-Hope-School/Project-FE)         | 프론트엔드 (React)   |
| **Project-BE** (여기)                                                   | 백엔드               |
| [Project](https://github.com/Tmax-IT-Hope-School/Project)               | FE·BE 공용 토론      |

할 일은 [이슈](https://github.com/Tmax-IT-Hope-School/Project-BE/issues)와
조직 보드 **AI 스터디 비서** 에 있습니다. 처음이라면 `good first issue` 라벨부터 고르세요.

---

## 🚀 실행 방법

처음 하는 사람도 **위에서부터 그대로 따라 치면** 서버가 켜집니다. DB 는 따로 설치하지 않습니다.

### 0. 준비물 (처음 한 번만)

| 필요한 것  | 확인 명령       | 없으면                                                                                   |
| ---------- | --------------- | ---------------------------------------------------------------------------------------- |
| Git        | `git --version` | [git-scm.com](https://git-scm.com) 에서 설치                                             |
| **JDK 21** | `java -version` | Mac `brew install --cask temurin@21` / Windows `winget install EclipseAdoptium.Temurin.21.JDK` |
| gh         | `gh --version`  | Mac `brew install gh` / Windows `winget install --id GitHub.cli` — PR 올릴 때 씁니다     |
| jq         | `jq --version`  | Mac `brew install jq` / Windows `winget install jqlang.jq` — Claude 안전장치 훅이 씁니다 |
| Node.js    | `node -v`       | [nodejs.org](https://nodejs.org) — GitHub MCP 연결 스크립트만 씁니다 (선택)              |

> JDK(자바 개발 도구)는 **21 버전**이어야 합니다. `java -version` 을 쳤을 때 `21` 로 시작하면 준비 끝입니다.
> 설치 프로그램이 편하면 [adoptium.net](https://adoptium.net) 에서 **Temurin 21** 을 받으세요.
> Gradle(빌드 도구)은 설치하지 않습니다. 저장소에 든 `gradlew` 가 알아서 받습니다.

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

### 3. 서버 켜기

```bash
./gradlew bootRun          # Windows:  .\gradlew.bat bootRun
```

- **처음 한 번은 몇 분 걸립니다.** Gradle 과 라이브러리를 내려받는 중이니 기다리세요.
- `Started ProjectBeApplication` 이 보이면 켜진 것입니다. 진행률이 `80% EXECUTING` 에서 멈춘 것처럼 보여도 **정상**입니다. (서버가 켜져 있는 동안 계속 그렇게 보입니다)
- 서버를 끄려면 터미널에서 `Ctrl + C`.
- 코드를 고쳤으면 **껐다가 다시 켜야** 반영됩니다.

### 4. 잘 켜졌는지 확인하기

브라우저에서 👉 **http://localhost:8080/api/v1/health**

```json
{ "status": "ok" }
```

이렇게 나오면 성공입니다.

### 5. API 직접 불러 보기 (Swagger)

서버를 켠 채로 👉 **http://localhost:8080/swagger-ui.html**

우리가 만든 API 가 전부 나오는 **메뉴판**입니다. API 를 펼치고 **Try it out → Execute** 를 누르면 직접 불러 볼 수 있습니다.
"요청", "응답", "200" 이 무슨 말인지 모르겠다면 [docs/API-TEST-GUIDE.md](./docs/API-TEST-GUIDE.md) 부터 읽으세요.

### 6. DB 들여다보기 (로컬 H2)

로컬에서는 **H2 메모리 DB** 를 씁니다. 서버 안에 들어 있어서 설치할 것이 없습니다.

- 서버를 켠 채로 👉 **http://localhost:8080/h2-console**
- `JDBC URL` 에 `jdbc:h2:mem:projectbe`, `User Name` 에 `sa`, 비밀번호는 비우고 **Connect**
- ⚠️ **서버를 끄면 데이터가 전부 사라집니다.** 연습용이라 그렇습니다. 켤 때마다 깨끗한 상태로 시작합니다.

테이블이 서로 어떻게 이어지는지는 [docs/DB.md](./docs/DB.md) 의 **다이어그램**으로 봅니다.
테이블을 만들 때마다 Claude Code 에서 `/db` 를 치면 그림을 새로 그려 브라우저로 보여 줍니다.

운영 DB(Neon) 는 다음 단계에서 연결합니다. 지금은 계정도, 접속 주소도 필요 없습니다.

### 7. Claude Code 로 나머지 세팅

```bash
claude
```

"MCP 서버를 사용할까요?" 에 **예** → `/setup` 을 치면 준비물·빌드·GitHub 로그인·MCP 연결까지 한 단계씩 확인해 줍니다.

> 막히면 [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md) 를 먼저 보세요. 자주 나는 에러를 모아 두었습니다.

---

## 📜 명령어 모음

| 명령                            | 하는 일                                           |
| ------------------------------- | ------------------------------------------------- |
| `./gradlew bootRun`             | 서버 켜기 (작업할 때 쓰는 것)                     |
| `./gradlew test`                | 테스트만 돌리기                                   |
| `./gradlew spotlessApply`       | 코드 모양 자동 정리                               |
| `./gradlew spotlessCheck build` | 전체 검사: 정리 검사 + 빌드 + 테스트 (CI 와 같음) |

Windows 는 `./gradlew` 자리에 `.\gradlew.bat` 을 씁니다.

**커밋하기 전에는 아래 두 줄이 통과해야 합니다.**

```bash
./gradlew spotlessApply
./gradlew spotlessCheck build
```

> Claude Code 를 쓴다면 `/check` 한 줄이면 됩니다.
> PR 을 올리면 GitHub 가 같은 검사를 한 번 더 돌립니다. (`.github/workflows/ci.yml`)

---

## 🧰 기술 스택

| 구분       | 사용 기술                                                                  |
| ---------- | -------------------------------------------------------------------------- |
| 언어       | **Java 21**                                                                |
| 프레임워크 | **Spring Boot 4.1** (Web MVC, Data JPA, Validation)                        |
| 빌드 도구  | **Gradle** — 저장소에 든 `gradlew` 사용 (따로 설치하지 않음)               |
| 코드 정리  | Spotless (google-java-format)                                              |
| 테스트     | JUnit 5                                                                    |
| API 확인   | **Swagger UI** (springdoc-openapi)                                         |
| DB         | 로컬: **H2 메모리 DB** / 원격: Neon (PostgreSQL) 으로 옮길 예정            |
| AI         | OpenAI GPT API — 서버에서만 호출                                           |
| 배포       | 무료 서비스 예정 — 미정 (이슈 #8). `Dockerfile` 은 준비돼 있습니다         |

---

## 🤖 Claude Code 로 작업하기

이 프로젝트는 코딩이 처음인 사람도 할 수 있게 **슬래시 명령어**를 만들어 두었습니다.
터미널에서 `claude` 를 실행한 뒤 아래를 치세요.

| 명령                          | 하는 일                                                   |
| ----------------------------- | --------------------------------------------------------- |
| `/setup`                      | **최초 1회** — 준비물·빌드·git 훅·GitHub 로그인·MCP 점검  |
| `/start 공부 계획 API 만들기` | **브랜치 이름을 추천해서 물어보고** 새 브랜치를 만듭니다  |
| `/save`                       | 검사를 돌리고 **커밋 메시지까지 알아서 써서** 저장        |
| `/pr`                         | 푸시 + PR 생성 (**체크리스트도 직접 검증해서 채움**)      |
| `/run`                        | 서버 켜기                                                 |
| `/swagger`                    | 만든 API 를 Swagger 에서 **직접 불러 보며** 확인          |
| `/db`                         | 지금 테이블을 **다이어그램으로** 그려서 보여주기          |
| `/check`                      | 코드 정리 + 빌드 + 테스트를 한 번에                       |
| `/sync`                       | 팀원이 올린 최신 코드 가져오기                            |
| `/fix <에러 메시지>`          | 에러가 무슨 뜻인지 설명하고 같이 고치기                   |

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

- **로컬에서 서버를 켜는 데는 비밀값이 하나도 필요 없습니다.** (`.env` 를 만들지 않아도 됩니다)
- `DATABASE_URL`, `OPENAI_API_KEY` 같은 값은 코드에 쓰지 않습니다. 설정 파일이 환경변수에서 읽습니다.
- 저장소에는 빈 값의 `.env.example` 만 올립니다.
- `.env` 는 `.gitignore` 와 git 훅이 이중으로 막습니다.
- 실수로 올렸다면 **바로 팀장에게 알리고 키를 새로 발급**하세요. 지우는 것만으로는 기록에서 사라지지 않습니다.

---

## 📁 폴더 구조

```
src/main/java/com/ithopeschool/projectbe/
├── ProjectBeApplication.java   앱 시작점
├── global/                     여러 기능이 같이 쓰는 것 (설정, 공통 예외 등)
│   └── config/WebConfig.java   CORS 설정
└── domain/                     기능별 폴더 하나씩
    └── health/                 예시: Controller + 응답 DTO
src/main/resources/
├── application.yml             공통 설정
├── application-local.yml       로컬 (H2)
└── application-prod.yml        배포 (환경변수로 DB 주입)
src/test/java/                  테스트
docs/                           팀 문서
.githooks/                      커밋·푸시 검사 (git 훅)
.claude/                        Claude Code 설정·훅·명령어·스킬
```

"이 파일 어디에 만들지?" 는 [docs/FOLDER-STRUCTURE.md](./docs/FOLDER-STRUCTURE.md) 에 있습니다.

---

## 📚 문서

| 문서                                                   | 언제 읽나                      |
| ------------------------------------------------------ | ------------------------------ |
| [docs/FOLDER-STRUCTURE.md](./docs/FOLDER-STRUCTURE.md) | 새 파일을 만들기 전에          |
| [docs/API-TEST-GUIDE.md](./docs/API-TEST-GUIDE.md)     | 요청·응답이 뭔지, Swagger 쓰는 법 |
| [docs/DB.md](./docs/DB.md)                             | 테이블 다이어그램              |
| [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md)   | 에러가 났을 때                 |
| [docs/GIT-GUIDE.md](./docs/GIT-GUIDE.md)               | git/GitHub 가 처음일 때        |
| [docs/CONVENTIONS.md](./docs/CONVENTIONS.md)           | 브랜치·커밋·PR 규칙 (한 장)    |
| [docs/AI-GUIDE.md](./docs/AI-GUIDE.md)                 | Claude Code·안전장치·MCP       |
| [CONTRIBUTING.md](./CONTRIBUTING.md)                   | 협업 규칙 요약                 |
| [CLAUDE.md](./CLAUDE.md)                               | AI가 지키는 규칙 (사람도 읽기) |
