# AGENTS.md

이 레포에서 작업하는 사람과 AI 에이전트(Claude Code, Codex 등)가 따르는 규칙입니다.

## 프로젝트

- 학생·입시생 자기관리 앱의 백엔드 API 서버 (Java 21, Spring Boot 4.1, Gradle)
- 프론트엔드: https://github.com/Tmax-IT-Hope-School/Project-FE
- 팀원 대부분이 입문자입니다. 코드는 **짧고 읽기 쉽게**, 똑똑한 트릭보다 평범한 코드를 씁니다.

## 명령어

| 하려는 것 | 명령 |
| --- | --- |
| 서버 실행 | `./gradlew bootRun` |
| 테스트 | `./gradlew test` |
| 코드 정리 | `./gradlew spotlessApply` |
| 전체 검사 (CI와 동일) | `./gradlew spotlessCheck build` |

작업을 끝내기 전에 **`./gradlew spotlessApply` 후 `./gradlew build`를 통과**시킵니다. CI에서도 똑같이 검사합니다.

## 코드 구조 규칙

기능(도메인)별로 패키지를 나눕니다: `com.ithopeschool.projectbe.domain.<기능>`

```
domain/<기능>/
├── <기능>Controller.java   # API 입구. 요청 받고 Service 호출만 (얇게)
├── <기능>Service.java      # 실제 로직, @Transactional 은 여기
├── <기능>Repository.java   # JpaRepository 상속
├── <기능>.java             # JPA 엔티티
└── dto/                    # 요청·응답 모양 (record 사용)
```

- 여러 기능이 같이 쓰는 설정·예외 처리는 `global/` 아래에 둡니다.
- Controller는 엔티티를 그대로 반환하지 않고 항상 DTO(record)로 반환합니다.
- 요청 DTO에는 `@Valid` + Bean Validation(`@NotBlank` 등)으로 입력을 검사합니다.
- 의존성 주입은 생성자 주입(`@RequiredArgsConstructor` + `private final`)만 씁니다. `@Autowired` 필드 주입 금지 (테스트 코드 제외).
- Lombok은 `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = PROTECTED)` 정도만. 엔티티에 `@Setter`, `@Data` 금지.
- URL은 `/api/v1/` 아래 소문자 kebab-case 복수형(`/api/v1/study-sessions`), JSON 필드는 camelCase.
- 설정값·비밀키는 `application-*.yml`에서 `${환경변수}`로 읽습니다. 코드에 키를 직접 쓰지 않고, 새 값은 `.env.example`에도 추가합니다.
- API를 만들거나 고치면 테스트를 같이 씁니다. Controller는 `@WebMvcTest`, Service는 단위 테스트, Repository는 `@DataJpaTest`.

## Git 규칙

- `main`에 직접 push 금지. 항상 브랜치 → PR.
- 브랜치 이름: `<타입>/<이슈번호>-<짧은설명>` (예: `feat/12-timer-api`, `fix/20-health-500`)
- 커밋 메시지: `<타입>: <무엇을 했는지>` (예: `feat: 타이머 시작 API 추가`)
  - 타입: `feat` 기능 · `fix` 버그 · `refactor` 리팩터 · `test` 테스트 · `docs` 문서 · `chore` 설정·기타
- PR 본문에 `Closes #이슈번호`를 써서 이슈와 연결합니다.
- PR 하나에는 이슈 하나만. 커지면 쪼갭니다.
- merge는 리뷰 1명 승인 + CI 통과 후 squash merge.

## 하지 말 것

- `.env`, API 키, 비밀번호 커밋 (레포가 public입니다)
- 테스트를 지우거나 `@Disabled`로 꺼서 CI 통과시키기
- 이슈와 상관없는 파일 대량 수정·포맷 변경
- 합의 없이 Spring Security 등 큰 라이브러리 추가 (넣으면 모든 API 동작이 바뀝니다)
