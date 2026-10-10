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
- API 키·DB 비밀번호처럼 보이는 글자가 든 커밋
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
{ "status": "ok", "db": "connected" }
```

이렇게 나오면 성공입니다. (`db` 는 DB 가 붙어 있는지 알려 줍니다. DB 에 못 붙으면 `"db": "error"` 가 나오지만 서버는 꺼지지 않습니다.)

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
| 배포       | **Render** 무료 서버 (이슈 #8) — 아래 [배포](#-배포-무료-서버) 와 [docs/DEPLOY.md](./docs/DEPLOY.md) |

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

- **로컬에서 서버를 켜는 데는 비밀값이 하나도 필요 없습니다.**
- BE 는 `.env` 파일을 쓰지 않습니다. 설정은 `src/main/resources/application-*.yml` 에 있고, git 으로 모두가 같은 것을 씁니다.
- 내 컴퓨터에서만 통하는 값(로컬 DB 주소 등)은 `application-local.yml` 에 적혀 있습니다. 올려도 괜찮은 값입니다.
- **진짜 서비스의 값**(운영 DB 비밀번호, GPT 키, SNS 로그인 비밀값)은 어떤 파일에도 적지 않습니다.
  설정 파일에는 `${DATABASE_URL}` 처럼 **값이 들어갈 자리만** 있습니다. 받는 방법은 이슈 #76 에서 정합니다.
- 키나 비밀번호처럼 보이는 글자가 든 커밋은 git 훅이 막습니다.
- 실수로 올렸다면 **바로 팀장에게 알리고 키를 새로 발급**하세요. 지우는 것만으로는 기록에서 사라지지 않습니다.

---

## 🌐 배포 (무료 서버)

BE 를 인터넷에 올려서 FE(Vercel) 가 부를 수 있게 합니다. **Render** 의 무료 서비스를 씁니다. (이슈 #8)
설정 파일은 [`render.yaml`](./render.yaml), 단계별 방법은 👉 [docs/DEPLOY.md](./docs/DEPLOY.md) 입니다.

| 환경              | Git 브랜치  | Neon DB 브랜치 | 서버      |
| ----------------- | ----------- | -------------- | --------- |
| **배포** (인터넷) | `main`      | `main`         | Render    |
| **개발** (내 컴퓨터) | `develop` 에서 딴 작업 브랜치 | `dev` | 내 컴퓨터 |

### 어디에 올릴까 — 후보 비교 (2026-10-10 확인)

**카드(결제수단) 없이** 쓸 수 있는 곳만 비교했습니다. 무료 조건은 자주 바뀌니, **고르기 전에 공식 문서를 다시 확인**하세요.

| 후보                                                                                    | 무료 한도                                                  | 잠드나?                                          | 속도                                              | 우리 BE 에 맞나?                                                        |
| --------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------------------ | ------------------------------------------------- | ----------------------------------------------------------------------- |
| **Render** ✅ ([무료 플랜](https://render.com/docs/free), [설정 파일](https://render.com/docs/blueprint-spec)) | 메모리 **512MB**, CPU 0.1, 월 **750시간**, Docker 가능      | **잠듭니다.** 15분 동안 요청이 없으면 자고, 깨우는 데 약 1분 | 아시아(**싱가포르**) 지역이 있음. 잠든 뒤 첫 요청은 느림 | ✅ 자바 서버를 켜 볼 만한 메모리. `Dockerfile` 을 그대로 씀               |
| Back4app Containers ([가격](https://www.back4app.com/pricing/container-as-a-service))     | 메모리 **256MB**, CPU 0.25, 전송 100GB, 카드 불필요, Docker 가능 | 공식 페이지에 없음                               | 지역: 미국                                        | ❌ 256MB 는 자바(Spring Boot) 서버에 너무 작을 가능성이 큼 (켜 보지는 않음) |
| Railway ([무료 체험](https://docs.railway.com/reference/pricing/free-trial))             | 가입 체험 **$5 크레딧(30일)**, 이후 월 **$1** 크레딧         | 공식 페이지에 없음                               | 공식 페이지에 없음                                | ❌ 30일 뒤에는 월 $1 로 서버를 계속 켜 두기 어려울 가능성이 큼             |

**후보에서 뺀 곳** (아래는 공식 페이지로 직접 확인하지 못했습니다)

- **Koyeb**: 공식 요금 페이지에 무료 서버 조건이 나오지 않고, 블로그들은 "가입에 카드 필요", "신규 가입 제한"으로 엇갈립니다.
- **Fly.io**: 블로그 기준 무료 플랜이 사실상 없고(짧은 체험 뒤 카드 필요), 자바 서버가 기본 설정으로 안 켜졌다는 후기가 있습니다.
- **Hugging Face Spaces**: 검색 결과 기준 Docker 공간은 유료 구독이 필요하다고 합니다.
- **Google Cloud Run**: `Dockerfile` 첫 줄 주석이 원래 이 서비스를 가리켰습니다. 가입에 카드 등록이 필요하다고 알려져 있어 "카드 없이" 조건에 맞지 않아 뺐습니다.

### 왜 Render 로 정했나

1. 카드 없이 쓸 수 있는 후보 중 **자바 서버를 켜 볼 만한 메모리(512MB)** 를 주는 곳이 Render 하나였습니다.
2. `Dockerfile` 을 그대로 쓰고, 서버 설정을 `render.yaml` 한 파일로 **코드에 남길** 수 있습니다. 비밀값은 `sync: false` 로 파일 밖(Render 화면)에만 둡니다.
3. 아시아(싱가포르) 지역을 고를 수 있어 한국에서 가깝습니다.

### 알아 둘 단점과, 아직 확인하지 못한 것

- ⚠️ **서버가 잠듭니다.** 요청이 15분 없으면 자고 깨우는 데 약 1분 걸립니다. **정해진 시각에 서버 작업을 돌리는 기능(#9)이 자는 동안 실행되지 않으니** #9 를 만들 때 같이 정해야 합니다.
- ⚠️ **가입할 때 카드를 요구하는지는 공식 문서에 없습니다.** 가입 화면에서 확인하고, 요구하면 멈춰서 팀에 알립니다.
- ⚠️ **CPU 가 0.1 이라 켜지는 데 오래 걸립니다.** 내 컴퓨터에서 켜 보니 켜지는 동안 CPU 를 약 8초 썼고, CPU 가 0.1 이면 그 10배인 **80초 이상** 걸린다는 계산입니다(추정). Render 가 켜지기를 얼마나 기다려 주는지는 문서에 없으니 첫 배포에서 확인합니다.
- ⚠️ **Render 에서 실제로 켜 본 것은 아닙니다.** 내 컴퓨터에서 메모리를 512MB 인 것처럼 제한하고 `render.yaml` 의 `JAVA_TOOL_OPTIONS` 로 켜 보니 켜진 직후 약 190MB 를 썼습니다. 다만 DB 가 안 붙은 상태였고 Docker 로 켠 것도 아닙니다.
- 무료 서버는 파일을 저장해도 다시 배포하면 사라집니다. 데이터는 꼭 Neon 에 저장합니다.

---

## 📁 폴더 구조

```
src/main/java/com/ithopeschool/projectbe/
├── ProjectBeApplication.java   앱 시작점
├── global/                     여러 기능이 같이 쓰는 것 (설정, 공통 예외 등)
│   └── config/WebConfig.java   CORS 설정
└── domain/                     기능별 폴더 하나씩
    └── health/                 예시: Controller + Service + 응답 DTO
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
| [docs/DEPLOY.md](./docs/DEPLOY.md)                     | 서버를 인터넷에 올릴 때        |
| [docs/GIT-GUIDE.md](./docs/GIT-GUIDE.md)               | git/GitHub 가 처음일 때        |
| [docs/CONVENTIONS.md](./docs/CONVENTIONS.md)           | 브랜치·커밋·PR 규칙 (한 장)    |
| [docs/AI-GUIDE.md](./docs/AI-GUIDE.md)                 | Claude Code·안전장치·MCP       |
| [CONTRIBUTING.md](./CONTRIBUTING.md)                   | 협업 규칙 요약                 |
| [CLAUDE.md](./CLAUDE.md)                               | AI가 지키는 규칙 (사람도 읽기) |
