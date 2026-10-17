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

처음 시작할 때는 운영 DB(Neon) 계정도, 접속 주소도 필요 없습니다. 데이터를 남겨 두고 확인하고 싶을 때만 아래 **🗄️ 운영 DB (Neon)** 부분을 봅니다.

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

## 🗄️ 운영 DB (Neon)

로컬 H2 는 서버를 끄면 비워지는 연습용입니다. 데이터가 남아야 하는 곳은 **Neon**(인터넷에 있는 PostgreSQL)을 씁니다. (이슈 #75)

| Neon 브랜치 | 누가 쓰나                         | 켜는 법                                            |
| ----------- | --------------------------------- | -------------------------------------------------- |
| `main`      | 배포 서버(Render) 만              | 배포 서버의 환경변수 ([docs/DEPLOY.md](./docs/DEPLOY.md)) |
| `dev`       | 내 컴퓨터에서 Neon 으로 시험할 때 | `SPRING_PROFILES_ACTIVE=local,neon` + 아래 4번      |

- **아무 설정 없이 켜면 지금처럼 H2** 로 켜집니다. Neon 은 필요할 때만 씁니다.
- **테이블은 서버가 만들지 않습니다.** 사람이 [`db/schema.sql`](./db/schema.sql) 을 Neon 에서 한 번 실행해 만들고, 서버는 엔티티와 맞는지 **검사만** 합니다. (아래 "왜 이렇게 정했나")
- ⚠️ Neon 가입·주소 확인은 **사람이 직접** 합니다. 접속 주소·비밀번호는 Claude 대화·이슈·PR·단체방·파일 어디에도 붙여 넣지 않습니다.
- ⚠️ `main` 브랜치에는 연습 쿼리를 돌리지 않습니다.

### 1. 가입 → 프로젝트 → 브랜치 (선생님·팀장이 처음 한 번)

1. [neon.com](https://neon.com) 에 가입합니다. (GitHub 계정으로 가입할 수 있습니다)
2. **New Project** — 이름은 `project-be`, 지역(Region)은 배포 서버와 가까운 **Singapore** 를 고릅니다. (Render 서버가 싱가포르에 있습니다 — `render.yaml`) Postgres 버전은 기본값 그대로 둡니다.
3. 프로젝트를 만들면 **`main` 브랜치**가 저절로 생깁니다. 이것이 배포용입니다.
4. 왼쪽 **Branches → New branch** 에서 이름 `dev`, 부모(parent)는 `main` 으로 개발용 브랜치를 만듭니다.

### 2. 테이블 만들기 (브랜치마다 한 번)

1. 왼쪽 **SQL Editor** 를 열고, 위쪽에서 브랜치를 **`dev`** 로 고릅니다.
2. [`db/schema.sql`](./db/schema.sql) 의 내용을 **전부** 붙여 넣고 **Run**.
3. 왼쪽 **Tables** 에 테이블(지금은 25개)이 보이면 됩니다.
4. `dev` 로 서버가 잘 켜지는 것(아래 4번)을 확인한 **뒤에** 같은 일을 `main` 에서 한 번 더 합니다.

> 이미 테이블이 있는 브랜치에서 다시 Run 하면 `already exists` 에러가 나고 **아무것도 바뀌지 않습니다.** (지우는 문장은 없습니다)

### 3. 접속 주소 찾기 — 모양을 바꿔야 합니다

Neon 콘솔 → **Connect** → 브랜치 `dev` 고르기. **Connection pooling** 은 끄고(직접 연결) 나오는 문자열을 봅니다.

```
postgresql://아이디:비밀번호@호스트/DB이름?sslmode=require&channel_binding=require
```

Spring 은 이 모양을 그대로 못 읽습니다. 아래처럼 **세 개로 나눕니다.**

| 환경변수            | 넣을 값                                                  | 예 (가짜 값)                                                         |
| ------------------- | -------------------------------------------------------- | -------------------------------------------------------------------- |
| `DATABASE_URL`      | `jdbc:postgresql://호스트/DB이름?sslmode=require`         | `jdbc:postgresql://ep-example-123456.ap-southeast-1.aws.neon.tech/neondb?sslmode=require` |
| `DATABASE_USERNAME` | `postgresql://` 바로 뒤, `:` 앞의 아이디                  | `neondb_owner`                                                       |
| `DATABASE_PASSWORD` | `:` 뒤, `@` 앞의 비밀번호                                  | (진짜 값은 어디에도 적지 않습니다)                                   |

- 앞에 **`jdbc:`** 를 붙이고, 주소 안의 `아이디:비밀번호@` 는 **뺍니다.**
- 끝은 `?sslmode=require` 만 남깁니다. (`channel_binding=...` 은 지웁니다)

### 4. 내 컴퓨터에서 Neon(`dev`)으로 켜기

지금은 **환경변수**로 넣습니다. 시크릿 매니저를 통한 개발용 값 주입은 [docs/SECRETS.md](./docs/SECRETS.md)를 참고하세요.
환경변수는 **그 터미널 창에서만** 살아 있고, 창을 닫으면 사라집니다. 파일로 저장하지 않습니다.

```powershell
# Windows PowerShell — <> 자리에 3번에서 만든 값을 넣습니다
$env:SPRING_PROFILES_ACTIVE = "local,neon"
$env:DATABASE_URL = "jdbc:postgresql://<호스트>/<DB이름>?sslmode=require"
$env:DATABASE_USERNAME = "<아이디>"
$env:DATABASE_PASSWORD = "<비밀번호>"
.\gradlew.bat bootRun
```

```bash
# Mac / Git Bash
export SPRING_PROFILES_ACTIVE=local,neon
export DATABASE_URL='jdbc:postgresql://<호스트>/<DB이름>?sslmode=require'
export DATABASE_USERNAME='<아이디>'
export DATABASE_PASSWORD='<비밀번호>'
./gradlew bootRun
```

확인하기:

1. 로그에 `The following 2 profiles are active: "local", "neon"` 이 보입니다.
2. http://localhost:8080/api/v1/health 가 `{"status":"ok","db":"connected"}` 입니다.
3. Swagger 로 과목을 하나 만들고 → 서버를 껐다 켠 뒤 → 과목 목록에 **그대로 남아 있으면** 성공입니다.

- `local,neon` 은 Swagger·테스트 토큰 같은 로컬 편의 기능은 그대로 쓰고 **DB 만** Neon 으로 바꿉니다. (`application-neon.yml`)
- `dev` 브랜치는 팀이 같이 씁니다. 다른 사람이 만든 데이터가 보일 수 있고, 테스트 토큰의 1번 사용자도 같이 씁니다.
- H2 화면(`/h2-console`)은 꺼집니다. 데이터는 Neon 콘솔의 **Tables** 에서 봅니다.

| 이런 에러가 나면                                              | 원인과 해결                                                                 |
| ------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `'url' must start with "jdbc"`                                | `DATABASE_URL` 을 안 넣었거나 `postgresql://` 그대로 넣었습니다. 3번대로 바꿉니다. |
| `Schema validation: missing table [...]` / `missing column`    | 그 브랜치에 `db/schema.sql` 을 안 돌렸거나, 엔티티가 바뀌었는데 Neon 에 반영 안 됐습니다. |
| `password authentication failed`                              | 아이디·비밀번호가 틀렸습니다. Neon 의 **`dev`** 브랜치 것인지 확인합니다.     |
| `연결이 거부되었습니다` / `Connection refused`                 | 호스트 이름이 틀렸습니다. `jdbc:postgresql://` 뒤를 다시 확인합니다.          |

### 엔티티(테이블)를 바꿨을 때

1. `db/schema.sql` 도 **같은 커밋**에서 고칩니다. 안 고치면 `./gradlew build` 의 `SchemaSqlTest` 가 실패합니다.
   (PostgreSQL 문법이 헷갈리면 Hibernate 가 만들어 주는 SQL 을 참고합니다 — Claude 에게 "schema.sql 고쳐 줘" 라고 하면 됩니다)
2. 이미 테이블이 있는 Neon 브랜치에 넣을 **바뀐 부분**은 [`db/migrations/`](./db/migrations) 에 번호를 붙인 파일로 남깁니다. (예: `03-main-300.sql`)
   - **여러 번 실행해도 되게** 씁니다. `create table if not exists`, `add column if not exists`, 외래키·검사 규칙은 `drop constraint if exists` 다음 `add constraint`.
   - 파일 전체를 `begin;` ~ `commit;` 으로 묶습니다. 중간에 에러가 나면 하나도 안 바뀝니다.
   - `./gradlew build` 의 `MigrationSqlTest` 가 "최신 DB 에 두 번 실행해도 에러가 없는지" 검사합니다.

#### 이미 있는 Neon 을 최신으로 올리기 (어느 상태든 같은 순서)

`db/migrations/` 의 파일을 **01 부터 끝 번호까지 순서대로** SQL Editor 에 붙여 넣고 Run 합니다. `dev` 먼저, `main` 은 그다음.
전에 어디까지 실행했는지 몰라도 처음부터 다시 돌리면 됩니다. 이미 있는 것은 건너뛰고 없는 것만 만들어집니다.
빈 Neon 브랜치라면 이 파일들 대신 `db/schema.sql` 하나만 실행합니다.

### 왜 이렇게 정했나 — 서버가 아니라 사람이 테이블을 만듭니다

| 방법                                         | 좋은 점                                     | 아쉬운 점                                             |
| -------------------------------------------- | ------------------------------------------- | ----------------------------------------------------- |
| ✅ `db/schema.sql` 을 Neon SQL Editor 에서 실행 | SQL 이 눈에 보이고, 운영 DB 가 멋대로 안 바뀜 | 엔티티를 고칠 때마다 SQL 도 같이 고쳐야 함 → 테스트가 잊으면 알려 줌 |
| 서버가 테이블을 만들게 하기 (`ddl-auto: update`) | 손이 덜 감                                  | 운영 DB 가 코드에 따라 자동으로 바뀌어 사고가 나기 쉬움 |

미성년자의 기록이 담기는 DB 라서 **"코드 한 줄 실수로 운영 DB 가 바뀌는 일"이 없게** 하는 쪽을 골랐습니다.

---

## 🔐 비밀값

- **로컬에서 서버를 켜는 데는 비밀값이 하나도 필요 없습니다.**
- BE 는 `.env` 파일을 쓰지 않습니다. 설정은 `src/main/resources/application-*.yml` 에 있고, git 으로 모두가 같은 것을 씁니다.
- 내 컴퓨터에서만 통하는 값(로컬 DB 주소 등)은 `application-local.yml` 에 적혀 있습니다. 올려도 괜찮은 값입니다.
- **진짜 서비스의 값**(운영 DB 비밀번호, GPT 키, SNS 로그인 비밀값)은 어떤 파일에도 적지 않습니다.
  설정 파일에는 `${DATABASE_URL}` 처럼 **값이 들어갈 자리만** 있습니다. 개발용 값은 [시크릿 매니저 안내](./docs/SECRETS.md)의 선택안과 실행 순서를 따릅니다. 실제 가입·학생 실행 확인은 #76에 남아 있습니다.
- 키나 비밀번호처럼 보이는 글자가 든 커밋은 git 훅이 막습니다.
- 실수로 올렸다면 **바로 팀장에게 알리고 키를 새로 발급**하세요. 지우는 것만으로는 기록에서 사라지지 않습니다.

개발용 값이 필요한 담당자만 Infisical 개인 계정으로 실행합니다. 관리자가 개발 전용 프로젝트를 준비한 뒤:

1. [공식 설치 안내](https://infisical.com/docs/cli/install)로 CLI 설치 (Windows: `winget install infisical`).
2. `infisical login`으로 본인이 로그인합니다. 비밀값·토큰을 채팅에 붙이지 않습니다.
3. Windows는 `infisical run --env=dev --projectId=PROJECT_ID -- .\gradlew.bat --no-daemon bootRun`으로 실행합니다.
   macOS/Linux는 `.\gradlew.bat` 대신 `./gradlew`를 씁니다. `PROJECT_ID`는 개발 프로젝트 ID입니다.

CLI가 없어도 기존 `gradlew bootRun`으로 H2가 켜집니다. 운영 값은 Render에만 둡니다.
계정·토큰이 노출되면 접근을 회수하고, 접근 가능했던 실제 키도 폐기·재발급한 뒤 개발 서버를 다시 켭니다.
자세한 범위·무료 인원·실행 확인·복구 순서는 [docs/SECRETS.md](./docs/SECRETS.md)에 있습니다.

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
- ⚠️ **CPU 가 0.1 이라 켜지는 데 오래 걸립니다.** 내 컴퓨터에서 켜 보니 켜지는 동안 CPU 를 약 15초 썼고(2026-10-17), CPU 가 0.1 이면 그 10배인 **2분 30초 이상** 걸린다는 계산입니다(추정). Render 는 상태 확인 통과를 [15분까지 기다려 주므로](https://render.com/docs/health-checks) 그 안에는 들어올 것으로 보지만, 첫 배포에서 확인합니다.
- **2026-10-17 배포 응답 확인:** [실제 헬스 주소](https://project-be-mxxc.onrender.com/api/v1/health)에서 HTTP 200, `{"status":"ok","db":"connected"}` 를 확인했습니다. 주소 출처는 [#194](https://github.com/Tmax-IT-Hope-School/Project-BE/issues/194)입니다. 운영 DB의 `main` 브랜치 여부와 비밀값 설정은 관리자의 별도 확인이 남아 있습니다. 메모리 약 190MB / 기능 추가 후 약 240MB 수치는 로컬 측정이며 배포 서버 측정값은 아닙니다.
- 무료 서버는 파일을 저장해도 다시 배포하면 사라집니다. 데이터는 꼭 Neon 에 저장합니다.

---

## ⏰ 예약 작업

사람이 요청하지 않아도 **정해진 시각에 서버가 일을 하게** 하는 장치입니다. (이슈 #9)
예: 매일 밤 하루 요약표 만들기(#34), 계획 시간이 지났는데 타이머가 꺼져 있으면 메시지 보내기(#31)
사용법·새 작업 만드는 법·연결 순서는 👉 [docs/JOBS.md](./docs/JOBS.md) 입니다.

**방식: 서버가 "작업 주소"를 열어 두고, 바깥의 무료 스케줄러(cron-job.org)가 정해진 시각에 그 주소를 부릅니다.**
`POST /api/v1/jobs/{이름}` 을 비밀 키 헤더(`X-Job-Key`)와 함께 부르면 작업이 한 번 실행됩니다.

### 어디서 알람을 울릴까 — 방법 비교 (2026-10-10 확인)

카드(결제수단) 없이 쓸 수 있는 방법만 봤습니다. 무료 조건은 바뀔 수 있으니 **고르기 전에 공식 문서를 다시 확인**하세요.

| 방법 | 무료 한도·제약 | 잠드는 무료 서버와의 관계 | 우리 BE 에 맞나? |
| ---- | -------------- | ------------------------- | ---------------- |
| **cron-job.org** ✅ ([FAQ](https://cron-job.org/en/faq/)) | 무료. **1분 간격**까지, **POST·사용자 지정 헤더** 가능, 실패 시 **이메일 알림**, 응답은 **30초**까지만 기다림 | 바깥에서 부르므로 서버가 잠들어도 상관없음. 10분마다 부르면 서버가 **잠들 틈도 없음** | ✅ 10분 연습 작업과 나중의 매일 밤 요약을 모두 처리 |
| GitHub Actions 일정 실행 ([일정 이벤트](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows), [요금](https://docs.github.com/en/billing/managing-billing-for-your-products/managing-billing-for-github-actions/about-billing-for-github-actions)) | 최소 **5분** 간격, 부하가 많으면 **지연·누락** 가능, **기본 브랜치**의 파일만 동작, UTC 기준. 이 저장소는 **비공개라 월 2,000분**만 무료이고 넘으면 **차단** | 바깥에서 부르므로 상관없음 | ❌ 10분마다 = 월 4,320번이라 열흘 안에 한도를 넘고 **PR 검사(CI)까지 멈출 수 있음** |
| Render Cron Job ([문서](https://render.com/docs/cronjobs)) | 서비스당 **월 최소 $1** (무료 플랜 안내 없음) | 별도 서비스로 돎 | ❌ "카드 없이" 조건에 맞지 않음 |
| 서버 안에서 직접(`@Scheduled`) | 추가 비용 없음 | **서버가 깨어 있을 때만** 동작. 무료 서버는 15분 유휴 시 잠듦 | ❌ 단독으로는 불안정. 바깥에서 불러 주면 필요 없음 |

### 왜 cron-job.org 로 정했나

1. **이슈의 "10분마다" 조건을 지킬 수 있는 무료 방법**이었습니다. (GitHub Actions 는 비공개 저장소의 무료 분 한도 때문에 불가능에 가깝습니다)
2. **서버가 잠드는 문제를 같이 풉니다.** 10분마다 서버를 부르니 15분 유휴 규칙에 걸리지 않습니다.
3. **서버 코드는 스케줄러와 상관이 없습니다.** 주소 하나와 비밀 키 확인뿐이라, 나중에 다른 스케줄러로 바꿔도 서버는 그대로입니다.

### 안전 규칙 (자세한 건 docs/JOBS.md)

- 🔒 **비밀 키**: `X-Job-Key` 가 없거나 틀리면 `401`. 서버에 키가 설정되지 않았으면 **전부 거절**합니다.
- ♻️ **두 번 돌아도 안전**: 같은 작업이 겹쳐 돌면 건너뛰고, 결과는 "추가"가 아니라 **그날 한 칸에 덮어쓰기**입니다. 날짜는 **새벽 4시 기준(한국 시간)**.

### 알아 둘 단점과, 아직 확인하지 못한 것

- ⚠️ **실제 배포 서버에서 10분마다 도는 것은 확인하지 못했습니다.** #8 배포가 먼저 필요합니다. 로컬에서는 테스트와 직접 실행으로 확인했습니다.
- ⚠️ **cron-job.org 가입 때 카드를 요구하는지는 공식 문서에 없습니다.** 요구하면 멈추고 팀에 알립니다.
- ⚠️ 서버가 자고 있을 때의 **첫 요청은 30초 안에 응답하지 못해 실패로 기록될 수 있습니다.** (깨어나는 데 1분 이상) 그 요청이 서버를 깨우는지는 직접 해 보지 않았습니다.
- 10분마다 부르면 서버가 계속 깨어 있어 **무료 750시간(월)을 거의 다 씁니다.** 연습이 끝나면 알람을 끄세요.

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
| [docs/JOBS.md](./docs/JOBS.md)                         | 예약 작업을 만들거나 연결할 때 |
| [docs/GIT-GUIDE.md](./docs/GIT-GUIDE.md)               | git/GitHub 가 처음일 때        |
| [docs/CONVENTIONS.md](./docs/CONVENTIONS.md)           | 브랜치·커밋·PR 규칙 (한 장)    |
| [docs/AI-GUIDE.md](./docs/AI-GUIDE.md)                 | Claude Code·안전장치·MCP       |
| [CONTRIBUTING.md](./CONTRIBUTING.md)                   | 협업 규칙 요약                 |
| [CLAUDE.md](./CLAUDE.md)                               | AI가 지키는 규칙 (사람도 읽기) |
