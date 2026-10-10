# 폴더 구조 설계 (`src/`)

"이 파일 어디에 만들지?" 를 고민하지 않게 하려고 만든 문서입니다.
**새 파일을 만들기 전에 이 문서를 보세요.**

---

## 1. 한눈에 보기

```
src/main/java/com/ithopeschool/projectbe/
│
├── ProjectBeApplication.java    🚪 시작점. 서버를 켭니다.                거의 안 건드림
│
├── global/                      🌐 여러 기능이 같이 쓰는 것
│   ├── auth/
│   │   └── CurrentUserProvider.java "지금 요청한 사용자" 번호를 주는 단 한 곳 (로그인 전엔 테스트 사용자)
│   └── config/
│       └── WebConfig.java           CORS 설정 (어느 FE 주소를 허락할지)
│
└── domain/                      📦 기능 하나 = 폴더 하나
    └── health/                      서버 상태 확인 (가장 작은 예시)
        ├── HealthController.java        GET /api/v1/health
        ├── HealthService.java           서버·DB 상태를 확인하는 로직
        └── HealthResponse.java          응답 모양 { "status": "ok", "db": "connected" }

src/main/resources/              ⚙️ 설정 파일
├── application.yml                  공통 설정
├── application-local.yml            내 컴퓨터용 (H2 메모리 DB)
└── application-prod.yml             배포용 (환경변수로 DB 주소를 받음)

src/test/java/com/ithopeschool/projectbe/    🧪 테스트 — main 과 같은 폴더 모양으로
```

---

## 2. 요청 하나가 지나가는 길

FE 가 "메모 목록 줘" 라고 요청하면, 서버 안에서 이렇게 지나갑니다.

```
FE  ──요청──▶  Controller  ──▶  Service  ──▶  Repository  ──▶  DB
    ◀──응답──     (입구)        (실제 일)      (DB 창구)
```

| 이름       | 맡은 일                                   | 비유                    |
| ---------- | ----------------------------------------- | ----------------------- |
| Controller | 요청을 받고, Service 를 부르고, 응답을 돌려줌 | 식당의 **주문 받는 사람** |
| Service    | 실제 로직 (계산·검사·저장 순서)           | **요리사**              |
| Repository | DB 에서 꺼내고 넣기                       | **냉장고 담당**         |
| Entity     | DB 테이블 한 줄의 모양                    | 냉장고 속 **재료**      |
| DTO        | FE 와 주고받는 모양                       | 손님에게 나가는 **접시** |

**왜 나누나요?** 한 파일에 다 쓰면 처음엔 편하지만, 기능이 늘면 어디를 고쳐야 할지 찾기 어려워집니다.
"주소가 이상하다 → Controller", "계산이 틀렸다 → Service", "DB 에서 못 찾는다 → Repository" 처럼 **볼 곳이 정해집니다.**

---

## 3. 기능 하나를 만들 때

기능 하나는 `domain/<기능>/` 폴더 하나에 모읍니다. 예: 메모(memo) — 우리 앱에는 없는, 설명용 예시입니다

```
domain/memo/
├── MemoController.java   API 입구
├── MemoService.java      실제 로직
├── MemoRepository.java   DB 창구
├── Memo.java             엔티티 (memos 테이블 한 줄)
└── dto/
    └── MemoResponse.java 응답 모양
```

아래는 **모양을 보여주는 예시**입니다. 메모는 우리 앱에 없는 기능이라, 이걸 보고 **자기 기능에 맞게 직접** 만들어 보세요.

### 🧱 엔티티 — DB 테이블 한 줄의 모양

```java
@Entity
@Table(name = "memos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 이 메모의 주인. 남의 데이터와 섞이지 않게 하는 기준입니다.
  private Long userId;

  // 메모 내용. 예: "수학 숙제 챙기기"
  private String content;

  public Memo(Long userId, String content) {
    this.userId = userId;
    this.content = content;
  }
}
```

- 필드를 `userId` 처럼 camelCase 로 쓰면 DB 컬럼은 `user_id` 로 **알아서** 만들어집니다.
- 로컬에서는 서버를 켤 때 이 클래스를 보고 **테이블이 자동으로 만들어집니다.** SQL 을 따로 안 써도 됩니다.
- `@Setter`, `@Data` 는 붙이지 않습니다. 값을 바꾸는 메서드가 필요하면 이름을 지어 직접 만듭니다.
- 시각은 `Instant`, 날짜는 `LocalDate` 를 씁니다.

### 🗄 Repository — DB 창구

```java
public interface MemoRepository extends JpaRepository<Memo, Long> {

  // 메서드 이름만 지으면 Spring 이 SQL 을 만들어 줍니다: where user_id = ?
  List<Memo> findByUserId(Long userId);
}
```

- **모든 조회에 `userId` 를 넣습니다.** `findAll()` 로 전부 꺼내면 남의 데이터까지 나옵니다.

### 🍳 Service — 실제 로직

```java
@Service
@RequiredArgsConstructor
public class MemoService {

  private final MemoRepository memoRepository;

  @Transactional(readOnly = true)
  public List<MemoResponse> getMemos(Long userId) {
    return memoRepository.findByUserId(userId).stream()
        .map(memo -> new MemoResponse(memo.getId(), memo.getContent()))
        .toList();
  }
}
```

- `@RequiredArgsConstructor` + `private final` 로 필요한 것을 받습니다. (생성자 주입)
- DB 를 바꾸는 메서드에는 `@Transactional`, 읽기만 하면 `@Transactional(readOnly = true)`.

### 🚪 Controller — API 입구

```java
@Tag(name = "메모")
@RestController
@RequestMapping("/api/v1/memos")
@RequiredArgsConstructor
public class MemoController {

  private final MemoService memoService;

  @Operation(summary = "내 메모 목록")
  @GetMapping
  public List<MemoResponse> getMemos() {
    Long userId = 1L; // 로그인이 생기기 전까지는 테스트 사용자 (이슈 #6)
    return memoService.getMemos(userId);
  }
}
```

- Controller 는 **얇게.** 계산이나 `if` 가 길어지면 Service 로 옮깁니다.
- `@Tag` 와 `@Operation` 은 Swagger 화면에 보이는 **한국어 설명**입니다. 새 API 에는 꼭 붙입니다.
- 주소는 `/api/v1/` 아래 **소문자 + 하이픈 + 복수형**. 예: `/api/v1/study-sessions`

### 🍽 DTO — 주고받는 모양

```java
public record MemoResponse(Long id, String content) {}
```

- **엔티티를 그대로 돌려주지 않습니다.** 항상 DTO 로 바꿔서 돌려줍니다.
  (엔티티를 그대로 주면 `userId` 처럼 FE 가 몰라도 되는 값까지 나가고, 테이블을 고칠 때 FE 가 같이 깨집니다)
- JSON 필드 이름은 FE 타입과 같은 camelCase.
- 요청 DTO 에는 검사 조건을 붙입니다.

```java
public record MemoCreateRequest(@NotBlank String content) {}

// Controller 에서 @Valid 를 붙여야 검사가 실제로 돌아갑니다.
@PostMapping
public MemoResponse create(@Valid @RequestBody MemoCreateRequest request) { ... }
```

---

## 4. "이건 어디에 만들지?" 판단표

위에서부터 읽어 내려가세요. **처음 걸리는 곳**이 정답입니다.

| 질문                                         | 예 → 여기                              |
| -------------------------------------------- | -------------------------------------- |
| 주소(URL)를 받는 곳인가?                     | `domain/<기능>/<기능>Controller.java`  |
| 계산·검사·저장 순서 같은 로직인가?           | `domain/<기능>/<기능>Service.java`     |
| DB 에서 꺼내거나 넣는가?                     | `domain/<기능>/<기능>Repository.java`  |
| DB 테이블 한 줄의 모양인가?                  | `domain/<기능>/<기능>.java`            |
| FE 와 주고받는 요청·응답 모양인가?           | `domain/<기능>/dto/`                   |
| **여러 기능**이 같이 쓰는 설정·예외 처리인가? | `global/`                              |
| DB 주소·포트 같은 설정값인가?                | `src/main/resources/application*.yml`  |
| 테스트인가?                                  | `src/test/java/...` 의 **같은 폴더**   |

### ⭐ 가장 중요한 원칙: **미리 빼지 않기**

- 한 기능에서만 쓰는 것은 그 기능 폴더 안에 둡니다. 두 번째 기능이 똑같이 필요해질 때 `global/` 로 옮깁니다.
- "나중에 쓸 것 같아서" 만드는 클래스·인터페이스는 만들지 않습니다.
- 길고 평범한 코드가, 짧고 똑똑한 코드보다 낫습니다.

---

## 5. 테스트는 어디에

`main` 과 **같은 폴더 모양**으로 `src/test/java/` 아래에 둡니다.

```
src/main/java/.../domain/health/HealthController.java
src/test/java/.../domain/health/HealthControllerTest.java     ← 이름 뒤에 Test
```

| 무엇을 검사하나 | 쓰는 것        | 특징                                |
| --------------- | -------------- | ----------------------------------- |
| Controller      | `@WebMvcTest`  | 서버 전체를 안 켜고 API 만 불러 봄  |
| Service         | 단위 테스트    | Spring 없이 메서드만 직접 부름      |
| Repository      | `@DataJpaTest` | 테스트용 DB 에 실제로 넣고 꺼내 봄  |

`HealthControllerTest.java` 가 가장 작은 예시입니다. 새 Controller 테스트는 이걸 복사해서 시작하세요.
