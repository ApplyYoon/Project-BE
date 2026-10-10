# CLAUDE.md

Claude Code(및 다른 AI 에이전트)가 이 저장소에서 작업할 때 **반드시** 지키는 규칙입니다.
사람이 읽어도 됩니다 — 팀 규칙이 전부 여기에 요약돼 있습니다.

> 이 프로젝트는 **고등학교 2학년 학생들의 첫 팀 프로젝트**입니다.
> 따라서 "동작하는 코드"보다 **"학생이 읽고 이해할 수 있는 코드"** 가 우선입니다.
> 그리고 **학생이 실수로 사고를 치지 않게 막는 것**이 속도보다 우선입니다.

---

## 0. 대원칙 5가지

1. **한국어로 대화한다.** 코드·변수명·커밋 타입은 영어, 설명·주석·커밋 본문은 한국어.
2. **학생이 이해 못 할 코드는 쓰지 않는다.** 화려한 추상화보다 길고 명확한 코드가 낫다.
3. **요청받은 것만 한다.** 시키지 않은 리팩터링·파일 삭제·라이브러리 추가 금지.
4. **되돌리기 어려운 일은 먼저 물어본다.** (force push, 브랜치 삭제, PR merge, 파일 대량 삭제, DB 테이블 삭제)
5. **거짓말하지 않는다.** 검증 안 한 건 "확인 안 했다"고 말한다. 실행이 깨졌으면 깨졌다고 말한다.

---

## 1. 기술 스택 (임의로 바꾸지 말 것)

| 구분       | 사용 기술                                                                     |
| ---------- | ----------------------------------------------------------------------------- |
| 언어       | Java 21                                                                       |
| 프레임워크 | **Spring Boot 4.1** (Web MVC, Data JPA, Validation)                           |
| 빌드 도구  | **Gradle** — 저장소에 든 `./gradlew` 로 실행 (Gradle 을 따로 설치하지 않음)   |
| 코드 정리  | Spotless (google-java-format)                                                 |
| 테스트     | JUnit 5                                                                       |
| API 확인   | **Swagger UI** (springdoc-openapi) — 만든 API 를 브라우저에서 직접 불러 봄    |
| DB         | 로컬: **H2 메모리 DB** (설치 불필요) / 원격: Neon (PostgreSQL) 으로 옮길 예정 |
| AI         | OpenAI GPT API — **서버에서만** 호출                                          |
| 배포       | 무료 서비스 예정 — 어디인지 미정 (이슈 #8). `Dockerfile` 은 준비됨            |

> 처음에는 FastAPI 로 정했다가(#1) **Spring Boot 로 바꿨습니다.**
> 이슈나 옛 글에 FastAPI·uv·psycopg 가 남아 있어도 **이 표가 기준**입니다.

**새 라이브러리 설치는 반드시 허락을 받고 하세요.** "이거 쓰면 편한데 설치할까요?" 라고 먼저 묻습니다.
(`build.gradle` 의 `dependencies` 에 줄을 추가하는 것이 라이브러리 설치입니다.)
특히 Spring Security 같은 큰 라이브러리는 넣는 순간 **모든 API 의 동작이 바뀌므로** 합의 없이 추가하지 않습니다.
**라이브러리를 늘리는 것보다, 지금 있는 것으로 만드는 법을 알려주는 게 이 프로젝트의 목적입니다.**

---

## 2. 폴더 구조

```
src/main/java/com/ithopeschool/projectbe/
├── ProjectBeApplication.java   앱 시작점
├── global/                     여러 기능이 같이 쓰는 것 (설정, 공통 예외 등)
│   └── config/WebConfig.java   CORS 설정
└── domain/                     기능별 폴더 하나씩
    └── health/                 예시: Controller + 응답 DTO
src/main/resources/             설정 파일 (application.yml, -local.yml, -prod.yml)
src/test/java/                  테스트 (main 과 같은 폴더 구조로)
build.gradle                    라이브러리 목록과 빌드 설정
gradlew, gradlew.bat, gradle/   Gradle 실행 파일 (고치지 않음)
docs/                           팀 문서 (폴더 구조, 문제 해결, git 가이드 등)
.githooks/                      커밋·푸시를 검사하는 git 훅
.claude/                        Claude Code 설정·훅·명령어·스킬
```

기능 하나는 `domain/<기능>/` 폴더 하나에 모읍니다.

```
domain/<기능>/
├── <기능>Controller.java   API 입구. 요청을 받고 Service 를 부르기만 (얇게)
├── <기능>Service.java      실제 로직. @Transactional 은 여기
├── <기능>Repository.java   DB 창구. JpaRepository 상속
├── <기능>.java             JPA 엔티티 (DB 테이블 한 줄의 모양)
└── dto/                    요청·응답 모양 (record 사용)
```

자세한 설명과 "이건 어디에 만들지?" 판단표: [docs/FOLDER-STRUCTURE.md](./docs/FOLDER-STRUCTURE.md)

---

## 3. 코드 작성 규칙

### 반드시

- **비밀값은 코드에 쓰지 않습니다.** `DATABASE_URL`, `OPENAI_API_KEY` 같은 값은 `application-*.yml` 에서 `${환경변수}` 로 읽습니다.
- 저장소에는 **빈 값의 `.env.example`** 만 올립니다. 새 비밀값이 생기면 `.env.example` 에 이름만 추가합니다.
- Controller 는 엔티티를 그대로 돌려주지 않고 **항상 DTO(record)** 로 돌려줍니다.
- 요청 DTO 는 `@Valid` + Bean Validation(`@NotBlank` 등)으로 입력을 검사합니다.
- 의존성 주입은 **생성자 주입**(`@RequiredArgsConstructor` + `private final`)만 씁니다. `@Autowired` 필드 주입은 테스트 코드에서만.
- Lombok 은 `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)` 정도만 씁니다.
- URL 은 `/api/v1/` 아래 **소문자 kebab-case 복수형**(`/api/v1/study-sessions`), JSON 필드는 **camelCase**.
- Controller 에는 `@Tag(name = "...")`, 메서드에는 `@Operation(summary = "...")` 로 **한국어 설명**을 붙입니다. Swagger 화면에 보이는 글자입니다.
- DB 테이블·컬럼은 **snake_case**. 엔티티 필드를 camelCase 로 쓰면 Spring 이 알아서 바꿉니다 (`userId` → `user_id`). 엔티티 필드마다 **한국어 주석** 한 줄.
- 시각은 `timestamptz`, 날짜는 `date`. 자바에서는 `Instant` / `LocalDate` 를 쓰면 그렇게 만들어집니다.
- 로컬에서는 서버를 켤 때 엔티티(`@Entity`)를 보고 **H2 에 테이블이 자동으로 만들어집니다.** (`ddl-auto: create-drop`)
- 모든 조회·수정에 **`user_id` 조건**을 붙입니다. 다른 사람의 데이터가 섞이면 안 됩니다.
- 사용자 입력은 **SQL 문자열에 직접 붙이지 않습니다.** `@Query` 를 직접 쓸 때도 반드시 파라미터(`:이름`)로 넘깁니다.
- API 를 만들거나 고치면 **테스트를 같이** 씁니다. Controller 는 `@WebMvcTest`, Service 는 단위 테스트, Repository 는 `@DataJpaTest`.
- 조금이라도 헷갈릴 코드에는 **한국어 주석 한 줄**.

### 금지

- 비밀값을 코드에 직접 쓰기, `.env` 커밋하기, 로그에 찍기.
- GPT API 키를 FE 에 주기. FE 에는 서버가 만든 **임시 키**만 줍니다.
- 에러를 무시 주석이나 빈 `catch` 로 덮기 — 원인을 고칩니다.
- 테스트를 지우거나 `@Disabled` 로 꺼서 검사를 통과시키기.
- 엔티티에 `@Setter`, `@Data` 붙이기 — 값이 어디서 바뀌는지 찾기 어려워집니다.
- 원격 DB(Neon)에 연습 쿼리 돌리기 — 연습은 로컬 H2 에서. (`http://localhost:8080/h2-console`)

### 테이블 설계는 학생이 합니다

테이블 설계는 이 프로젝트에서 **학생이 직접 하는 공부**입니다.
AI 는 테이블·컬럼을 **먼저 정해 주지 않습니다.** 질문으로 돕고, 학생이 정한 것을 코드로 옮기는 일을 돕습니다.
만들 때마다 `/db` 로 다이어그램을 새로 그려 눈으로 확인하게 합니다. (`.claude/skills/db-table/SKILL.md`)

### 원격 DB 는 아직입니다

지금은 **로컬 H2 만** 씁니다. 서버를 끄면 데이터가 사라지는 연습용 DB 입니다.
원격 DB(Neon) 연결은 다음 단계에서 하고, 그때 아래를 정해 이 문서에 적습니다.

- 원격 DB 의 테이블을 만드는 방법 (배포 설정은 `ddl-auto: validate` 라 **테이블이 미리 있어야** 합니다)
- 내 컴퓨터에서 접속 주소를 넣는 방법 (Spring Boot 는 `.env` 파일을 **알아서 읽지 않습니다**)

---

## 4. 개인정보와 안전 (미성년자 대상 서비스)

사용자 대부분이 고등학생입니다. 일기·기분·통화·건강 기록을 다룹니다.

- **분석에 쓰지 않는 정보는 모으지 않습니다.** SNS 로그인에서도 꼭 필요한 것만 받습니다.
- 일기·기분·통화 내용은 **로그에 찍지 않습니다.**
- 통화는 녹음 원본을 저장하지 않고 **텍스트 요약만** 남깁니다.
- 위기 표현 감지(안전 브레이크)는 **어떤 설정보다 우선**합니다. 채찍 기능보다 먼저 만듭니다.
- AI 가 사용자에게 하는 말에는 외모·성적·능력 비하, 다른 사람과 비교해 깎아내리기가 **절대 없어야** 합니다.

---

## 5. 작업이 끝나면 반드시 통과시킬 것

```bash
./gradlew spotlessApply          # 코드 모양 자동 정리
./gradlew spotlessCheck build    # 전체 검사: 정리 검사 + 빌드 + 테스트 (CI 와 같은 명령)
```

Windows 는 `./gradlew` 대신 `gradlew.bat` 을 씁니다. Claude Code 에서는 `/check` 한 줄이면 됩니다.

- API 를 만들거나 고쳤으면 서버를 켜서 **Swagger 로 실제로 불러 봅니다.** (`/swagger` — `http://localhost:8080/swagger-ui.html`)
  잘 되는 경우뿐 아니라 **일부러 틀리게 보낸 경우**(빈 값 → 400, 없는 번호 → 404)도 확인합니다.
- 엔티티를 만들거나 고쳤으면 `/db` 로 **다이어그램을 새로 그리고** `docs/DB.md` 에 반영합니다.
- `.githooks/` 나 `.claude/hooks/` 를 고쳤으면 **직접 실행해서** 막을 것은 막고 통과시킬 것은 통과시키는지 확인합니다.
- 원격 DB(Neon)에서 해야 하는 확인은 **사람에게 부탁합니다.** (AI 는 Neon 계정이 없습니다)
- 실패하면 **커밋하지 말고** 고친 뒤 다시 확인합니다.
- 고쳐도 안 되면 **에러 메시지 원문을 그대로 보여주고** 사람에게 물어봅니다.

---

## 6. Git 작업 규칙 (GitHub MCP / gh CLI 공통)

### 6-0. 브랜치 전략

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

- 작업 브랜치는 **항상 `develop` 에서** 딴다. (`main` 이 아니다)
- 작업이 끝나면 **`develop` 으로 PR** 을 올린다.
- `develop → main` PR 은 **사람이** 연다. AI 가 먼저 만들지 않는다.

### 6-1. 브랜치를 먼저 만든다 — 이름은 **물어본다**

**`main` 과 `develop` 에 직접 커밋하는 것은 절대 금지입니다.**
(`.claude/hooks/guard-git.sh` 와 `.githooks/` 가 실제로 막습니다. 차단당하면 우회하지 말고 규칙을 따르세요.)
작업 요청을 받으면 코드를 건드리기 **전에** 브랜치부터 만듭니다.

브랜치 이름은 **후보 3개를 만들어서 `AskUserQuestion` 으로 고르게 하세요.**
혼자 정하고 넘어가지도, 빈손으로 "뭐로 할까요?" 묻지도 마세요.

```
타입/영문-설명            feat/plan-api
타입/이슈번호-영문-설명   feat/12-plan-api
```

| 타입       | 언제                  |
| ---------- | --------------------- |
| `feat`     | 새 기능               |
| `fix`      | 버그 수정             |
| `refactor` | 동작 그대로 코드 정리 |
| `docs`     | 문서만                |
| `chore`    | 설정·패키지 등 기타   |
| `test`     | 테스트만              |

소문자·하이픈만. 한글·공백·대문자 금지.
한국어 → 영어 사전과 후보 만드는 요령: `.claude/commands/start.md`

```bash
git switch develop && git pull origin develop && git switch -c feat/12-plan-api
```

`develop` 이 없다는 에러가 나면: `git fetch origin && git switch -c develop origin/develop`

### 6-2. 커밋 메시지는 **당신이 쓴다**

사용자에게 "커밋 메시지 뭐로 할까요?" 라고 묻지 마세요. `git diff` 를 읽고 직접 씁니다.

```
<타입>: <한국어로 무엇을 했는지>      ← 50자 이내, 마침표 없음

<왜 그렇게 했는지 — 필요할 때만 2~3줄>

Refs: #12
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

**좋은 예**

```
feat: 날짜별 공부 계획 목록 API 추가

날짜 형식이 틀리면 400 을 돌려주고, 계획이 없는 날은
빈 배열을 돌려주도록 했습니다.

Refs: #11
```

**나쁜 예** — `수정` / `update` / `feat: 작업함` / `feat: 계획 API 만들고 스키마도 고침`

규칙:

- **한 커밋 = 한 가지 일.** 성격이 다르면 `git add` 를 나눠 커밋을 쪼갭니다.
- `git add .` 금지. 바꾼 파일을 명시합니다.

### 6-3. Pull Request

- **base 브랜치는 `develop`** — `gh pr create --base develop ...`
- 제목은 커밋과 같은 형식: `feat: 날짜별 공부 계획 목록 API 추가`
- 본문은 `.github/PULL_REQUEST_TEMPLATE.md` 를 **실제 내용으로** 채웁니다. 빈칸 금지.
- **체크리스트는 당신이 직접 검증해서 채웁니다.** 사용자에게 확인했냐고 묻지 마세요.
  단, **돌려보지 않은 항목을 체크하는 것은 절대 금지**입니다. 특히 "원격 DB 에서 확인" 처럼
  사람이 해야 하는 항목은 기본적으로 비워두고 사용자에게 안내합니다.
- 관련 이슈는 `Closes #12` 로 연결.
- 리뷰는 선택. 사용자가 원할 때만 리뷰어를 지정합니다. merge 는 PR 을 올린 사람이 직접 확인한 뒤 GitHub 웹에서 합니다.
- PR 하나는 **파일 10개 / 300줄 이내** 목표.

### 6-4. 절대 혼자 하지 않는 것

- `git push --force`, `git reset --hard`, `git rebase`
- 브랜치 삭제, PR merge / close
- `main` / `develop` 으로의 직접 push
- `develop → main` PR 생성·merge (사람이 합니다)
- 이미 올라간 커밋 수정 (`commit --amend`)
- 파일·폴더 삭제, 의존성 제거, DB 테이블 삭제(`DROP`)
- 저장소 설정 변경(공개 범위, 협업자 등)

이 중 대부분은 `.claude/hooks/guard-git.sh` 가 **실제로 차단**합니다.
force push(`--force`/`-f`/`+refspec`), `--mirror`, `--delete`, `--prune`,
`reset --hard`, `clean -f`, `checkout .`, `stash drop`, `filter-branch`,
`reflog expire`, `gc --prune`, `remote set-url`, `rm -rf .git`,
그리고 git 훅을 건너뛰는 `--no-verify`·`core.hooksPath` 변경입니다.

여기에 더해 `.githooks/` 의 git 훅이 커밋·푸시 시점에 한 번 더 검사합니다.
(main·develop 직접 커밋/푸시, `.env` 커밋, 커밋 메시지 형식, 강제 푸시, 브랜치 삭제 푸시)

> ⚠️ 이 저장소는 비공개 + 무료 요금제라 **GitHub 브랜치 보호를 켤 수 없습니다.**
> Claude 훅과 git 훅이 **유일한 안전장치**입니다. 그래서 더 엄격하게 지킵니다.

**차단당하면 우회하지 마세요.** 다른 명령으로 같은 결과를 내려는 시도도 금지입니다.
특히 `--no-verify` 로 git 훅을 건너뛰는 것은 **절대 금지**입니다.
왜 막혔는지 사용자에게 설명하고, 훅이 알려주는 안전한 대안을 제시하세요.

### 6-5. 작업 흐름 (요청 하나 = 이 순서)

1. **확인** — `git status` 로 현재 브랜치와 수정 파일 확인. 남의 작업이 섞여 있으면 멈추고 묻는다.
2. **브랜치** — 이름 후보를 제시해 고르게 하고, 최신 **`develop`** 에서 새 브랜치를 판다.
3. **작업** — 작은 단위로. 비밀값·개인정보 규칙을 지킨다.
4. **검증** — 5장의 확인을 전부 한다.
5. **커밋** — 컨벤션대로 직접 작성. 여러 일을 했으면 커밋을 나눈다.
6. **푸시** — `git push -u origin <브랜치명>`
7. **PR** — **base 는 `develop`.** 템플릿을 채우고 체크리스트를 검증해서 표시. 이슈 연결, 리뷰어는 원할 때만 지정.
8. **보고** — **PR 링크 + 바꾼 것 3줄 요약.**

**merge 는 사람이 합니다. AI는 merge 하지 않습니다.**

### 6-6. GitHub MCP 를 쓸 때

MCP 도구가 연결돼 있으면 `gh` CLI 대신 써도 됩니다. **규칙은 똑같습니다.**

- 브랜치 이름은 물어보고, 커밋 메시지는 컨벤션대로 직접 씁니다.
- MCP 로 파일을 직접 고쳐 `main`·`develop` 에 바로 올리는 기능은 쓰지 마세요.
  항상 로컬 브랜치 → 커밋 → 푸시 → PR 순서를 따릅니다.

---

## 7. 사람과 대화하는 방식

- 전문 용어는 **괄호로 짧게 풀어준다.** 예: "엔드포인트(서버의 주소 하나)"
- 코드를 고친 뒤에는 **"무엇을 / 왜" 2~3줄 요약.** 코드 전체를 다시 붙여넣지 않는다.
- 질문은 **한 번에 하나씩.** 3개를 한꺼번에 던지지 않는다.
- 학생이 틀린 방향으로 가도 **먼저 이유를 설명하고**, 그래도 하겠다면 그대로 해 준다.
- "이거 왜 이래요?" 에는 **고치기 전에 원인부터 설명**한다.
- 에러 메시지는 **한국어로 한 문장 번역** → **어느 파일 몇 번째 줄** → **어떻게 고칠지** 순서로.

---

## 8. 참고 문서

| 문서                                         | 내용                       |
| -------------------------------------------- | -------------------------- |
| [README.md](./README.md)                     | 저장소 소개, 처음 세팅     |
| [CONTRIBUTING.md](./CONTRIBUTING.md)         | 협업 규칙 요약             |
| [docs/CONVENTIONS.md](./docs/CONVENTIONS.md) | 브랜치·커밋·PR 치트시트    |
| [docs/GIT-GUIDE.md](./docs/GIT-GUIDE.md)     | git/GitHub 입문            |
| [docs/AI-GUIDE.md](./docs/AI-GUIDE.md)       | Claude Code·안전장치·MCP   |
| [docs/FOLDER-STRUCTURE.md](./docs/FOLDER-STRUCTURE.md) | 어느 파일을 어디에 만드나 |
| [docs/TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md)   | 자주 나는 에러와 해결법   |
| [docs/API-TEST-GUIDE.md](./docs/API-TEST-GUIDE.md)     | 요청·응답 개념, Swagger 사용법 |
| [docs/DB.md](./docs/DB.md)                             | DB 다이어그램 (`/db` 가 그림) |

스킬(`.claude/skills/`): `github-flow` `api-test` `db-table`
명령어(`.claude/commands/`): `/setup` `/start` `/run` `/swagger` `/db` `/check` `/save` `/pr` `/sync` `/fix`
