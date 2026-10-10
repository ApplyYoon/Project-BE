# 자주 나는 에러와 해결법

여기 없는 에러가 나면 `/fix <에러 메시지 붙여넣기>` 를 쓰세요.
해결한 뒤에는 **이 문서에 한 줄 추가해 주세요.** 다음 사람이 고생을 덜 합니다.

---

## 설치 / 실행

### `Cannot find a Java installation on your machine ... matching: {languageVersion=21`

**뜻**: 이 프로젝트는 JDK 21 로 만드는데, 내 컴퓨터에 21 버전이 없습니다. (17 같은 다른 버전만 있어도 이 에러가 납니다)

```bash
brew install --cask temurin@21                      # Mac
winget install EclipseAdoptium.Temurin.21.JDK       # Windows
```

다른 버전을 지울 필요는 없습니다. 21 을 **추가로** 설치하면 됩니다.
설치한 뒤에는 **터미널을 새로 열고** 다시 실행하세요.

### `JAVA_HOME is not set and no 'java' command could be found in your PATH`

**뜻**: 자바가 아예 없습니다. 위와 같은 방법으로 JDK 21 을 설치하고 터미널을 새로 여세요.

### `permission denied: ./gradlew`

**뜻**: `gradlew` 파일에 실행 권한이 없습니다. (Mac·Linux)

```bash
chmod +x gradlew
```

### Windows 에서 `./gradlew` 가 안 돼요

Windows 의 명령 프롬프트·PowerShell 에서는 이렇게 씁니다.

```
.\gradlew.bat bootRun
```

Git Bash 를 쓴다면 Mac 과 똑같이 `./gradlew bootRun` 이 됩니다.

### `Port 8080 was already in use`

**뜻**: 서버가 이미 켜져 있습니다. 다른 터미널 창에서 켜 둔 서버를 찾아 `Ctrl + C` 로 끄세요.

어느 창인지 모르겠다면 (Mac):

```bash
lsof -i :8080        # 8080 을 쓰고 있는 프로그램과 번호(PID)가 나옵니다
```

### 처음 실행이 몇 분째 안 끝나요

처음 한 번은 Gradle 과 라이브러리를 내려받느라 오래 걸립니다. 인터넷이 느리면 더 걸립니다.
두 번째부터는 몇 초면 켜집니다.

### `80% EXECUTING` 에서 멈춰 있어요

**정상입니다.** `Started ProjectBeApplication` 이 보였다면 서버는 켜져 있습니다.
서버가 켜져 있는 동안 계속 그렇게 보입니다. 끄려면 `Ctrl + C`.

### 코드를 고쳤는데 그대로예요

서버를 **껐다가 다시 켜야** 반영됩니다. `Ctrl + C` → `./gradlew bootRun`

---

## DB (로컬 H2)

### 서버를 껐다 켰더니 데이터가 다 사라졌어요

**정상입니다.** 로컬 DB 는 메모리에만 있는 연습용이라, 서버를 끄면 같이 사라집니다.
켤 때마다 깨끗한 상태로 시작합니다.

### H2 콘솔에서 `Database "..." not found` 가 나와요

**뜻**: 접속 주소가 다릅니다. H2 콘솔의 `JDBC URL` 칸에 처음 적혀 있는 값을 지우고 아래를 넣으세요.

```
JDBC URL:   jdbc:h2:mem:projectbe
User Name:  sa
Password:   (비워 두기)
```

### H2 콘솔 주소가 안 열려요

서버가 켜져 있어야 열립니다. `./gradlew bootRun` 을 한 뒤 http://localhost:8080/h2-console

### 엔티티를 만들었는데 테이블이 안 보여요

1. 클래스 위에 `@Entity` 가 있는지 확인
2. 파일이 `src/main/java/com/ithopeschool/projectbe/` 아래에 있는지 확인
3. 서버를 껐다 켰는지 확인 (테이블은 **켤 때** 만들어집니다)

---

## 검사 (`./gradlew spotlessCheck build`)

### `The following files had format violations`

**뜻**: 코드 모양(띄어쓰기·줄바꿈)이 규칙과 다릅니다. 직접 고치지 말고 자동 정리를 돌리세요.

```bash
./gradlew spotlessApply
```

### `There were failing tests. See the report at: ...`

**뜻**: 테스트가 실패했습니다. 메시지에 나온 `build/reports/tests/test/index.html` 을 브라우저로 열면
어느 테스트가 왜 실패했는지 보입니다.

테스트를 지우거나 `@Disabled` 로 꺼서 통과시키지 마세요. 원인을 고칩니다.

### `error: cannot find symbol`

**뜻**: 그런 이름을 못 찾았습니다. 오타이거나, 맨 위에 `import` 가 빠졌습니다.

```java
List<String> names = new ArrayList<>();   // ❌ import java.util.List; import java.util.ArrayList; 가 없으면 에러
```

에러 메시지의 `symbol:` 줄에 못 찾은 이름이, `location:` 줄에 어느 파일인지 나옵니다.

---

## Spring

### 주소를 불렀는데 404 가 나와요

1. 주소 오타 확인 — 이 프로젝트의 API 는 전부 `/api/v1/` 로 시작합니다
2. Controller 클래스 위에 `@RestController` 가 있는지 확인
3. 메서드 위에 `@GetMapping` / `@PostMapping` 이 있는지 확인
4. 서버를 껐다 켰는지 확인

### `required a bean of type '...' that could not be found`

**뜻**: Spring 이 그 클래스를 모릅니다. 대부분 클래스 위에 `@Service` 를 빼먹은 것입니다.

```java
@Service                       // ← 이게 없으면 Controller 가 Service 를 못 받습니다
@RequiredArgsConstructor
public class SubjectService { ... }
```

### `No property 'xxx' found for type 'Yyy'`

**뜻**: Repository 의 메서드 이름에 엔티티에 없는 필드 이름을 썼습니다. 오타를 확인하세요.

```java
List<Subject> findByUsrId(Long userId);    // ❌ usrId 라는 필드는 없음
List<Subject> findByUserId(Long userId);   // ✅
```

### 브라우저 콘솔에 `has been blocked by CORS policy` 가 나와요

**뜻**: FE 주소가 서버의 허락 목록에 없습니다.
기본으로 허락된 주소는 `http://localhost:5173` 과 `http://localhost:3000` 입니다.
FE 를 다른 포트로 켰다면 5173 으로 다시 켜거나, 팀장에게 `CORS_ALLOWED_ORIGINS` 에 주소 추가를 부탁하세요.

---

## Git / GitHub

### `Updates were rejected because the remote contains work that you do not have`

**뜻**: GitHub에 내가 모르는 커밋이 있습니다.

```bash
git pull origin develop   # 또는  /sync
```

`git push --force` 로 해결하려 하지 마세요. 팀원 작업이 사라집니다.

### 커밋했는데 "커밋 메시지 형식이 규칙과 다릅니다" 라고 막혀요

`타입: 설명` 형식이 아니어서 그렇습니다.

```
feat: 과목 목록 API 추가      ✅
과목 목록 API 추가            ❌ 타입 없음
feat 과목 목록 API 추가       ❌ 콜론(:) 없음
feat: 과목 목록 API 추가.     ❌ 끝에 마침표
```

타입은 `feat` `fix` `refactor` `style` `docs` `chore` `test` 중 하나입니다.
`/save` 를 쓰면 메시지를 알아서 써줍니다.

### 커밋·푸시가 "브랜치에는 직접 커밋할 수 없습니다" 라고 막혀요

브랜치를 안 만들고 `main` 이나 `develop` 에서 작업한 것입니다. 아직 커밋 전이면 그대로 브랜치만 만들면 됩니다.

```bash
git switch -c feat/작업이름
```

### git 훅이 안 도는 것 같아요

```bash
git config core.hooksPath            # .githooks 가 나와야 정상
git config core.hooksPath .githooks  # 비어 있으면 이걸로 켭니다
```

### `fatal: not a git repository`

프로젝트 폴더 밖에서 명령을 쳤습니다. `cd Project-BE` 로 들어가세요.

### `/mcp` 에서 github 가 `does not support dynamic client registration` 으로 실패해요

**뜻**: GitHub MCP 에 보낼 로그인 토큰을 못 구했습니다.
이 프로젝트는 `gh` 에 로그인된 토큰을 꺼내 씁니다.

```bash
gh auth status   # 로그인 안 돼 있으면 → gh auth login
node -v          # 토큰을 꺼내는 스크립트가 node 로 돕니다
```

로그인 후 Claude Code 를 껐다 켜세요. 그래도 안 되면 `claude` 를 프로젝트 폴더 맨 위에서 켰는지 확인하세요.
