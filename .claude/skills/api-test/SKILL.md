---
name: api-test
description: API(Controller)를 새로 만들거나 고쳤을 때 항상 읽으세요. Swagger 화면에 한국어 설명이 보이게 하고, 서버를 켜서 실제로 불러 보며 잘 만들었는지 확인하는 방법입니다. "API 확인", "Swagger", "스웨거", "요청·응답", "테스트 토큰" 관련 작업에도 씁니다.
---

# API 를 Swagger 로 확인하기

학생들은 "요청"과 "응답"이 아직 낯섭니다. **만든 API 는 반드시 Swagger 화면에서 직접 불러 보게** 합니다.
Swagger 는 우리가 만든 API 목록을 보여 주고, 버튼으로 직접 불러 볼 수 있는 화면입니다.

- 화면: http://localhost:8080/swagger-ui.html (서버가 켜져 있어야 열립니다)
- 요청·응답이 무엇인지: `docs/API-TEST-GUIDE.md`

## 1. Swagger 에 한국어 설명 달기

새 Controller 에는 `@Tag`, 새 메서드에는 `@Operation` 을 붙입니다. 없으면 Swagger 에 영어 메서드 이름만 보입니다.

```java
@Tag(name = "메모")                        // Swagger 에서 묶여 보이는 이름
@RestController
@RequestMapping("/api/v1/memos")
public class MemoController {

  @Operation(summary = "내 메모 목록")      // 이 API 가 하는 일 한 줄
  @GetMapping
  public List<MemoResponse> getMemos() { ... }
}
```

## 2. 확인 순서

1. **서버 켜기** — 이미 켜져 있으면 껐다가 다시 켭니다. (코드를 고치면 다시 켜야 반영됩니다)
   ```bash
   ./gradlew bootRun        # 백그라운드로 실행. Started ProjectBeApplication 이 보이면 켜진 것
   ```
2. **Swagger 에 보이는지** — 방금 만든 주소가 목록에 있어야 합니다.
   ```bash
   curl -s http://localhost:8080/v3/api-docs | jq '.paths | keys'
   ```
3. **직접 불러 보기** — 잘 되는 경우 하나, 잘못 보낸 경우 하나 이상.
   ```bash
   curl -i http://localhost:8080/api/v1/memos
   curl -i -X POST http://localhost:8080/api/v1/memos -H 'Content-Type: application/json' -d '{"content":""}'
   ```
4. **결과를 표로 보고** — 무엇을 보냈고, 무엇이 왔고, 그게 맞는지.

   | 보낸 것 | 온 것 | 맞나 |
   | --- | --- | --- |
   | `GET /api/v1/memos` | 200, `[]` | ✅ 처음엔 빈 목록 |
   | `POST` 내용을 비워서 | 400 | ✅ 빈 값은 거절 |

5. **학생이 직접 해 보게 안내** — 당신이 불러 본 것으로 끝내지 마세요. 아래를 그대로 안내합니다.
   > 1. 브라우저에서 http://localhost:8080/swagger-ui.html 을 엽니다
   > 2. 방금 만든 API 를 눌러 펼칩니다
   > 3. **Try it out** → 값을 채우고 → **Execute**
   > 4. 아래 **Code**(결과 번호)와 **Response body**(돌아온 내용)를 확인합니다

## 3. 잘 만들었는지 보는 기준

| 확인할 것 | 기대하는 결과 |
| --- | --- |
| 제대로 보냈을 때 | 200 번대 + 약속한 모양의 JSON |
| 필요한 값을 빼거나 비웠을 때 | 400 (서버가 멈추거나 500 이 나오면 안 됨) |
| 없는 번호(id)를 불렀을 때 | 404 |
| 남의 데이터를 불렀을 때 | 404 — 보이면 안 됩니다 |
| 응답 JSON 의 이름 | camelCase, 엔티티가 아니라 DTO |
| Swagger 화면 | 한국어 설명(`@Tag`, `@Operation`)이 보임 |

500 이 나오면 서버 로그의 `Caused by:` 를 읽고 원인을 고칩니다. 테스트를 끄거나 빈 `catch` 로 덮지 않습니다.

## 4. 로그인이 필요한 API — 테스트 토큰

진짜 SNS 로그인은 Swagger 에서 하기 어렵습니다. 그래서 **내 컴퓨터(local)에서만 통하는 테스트 토큰**을 씁니다.

- Swagger 화면 오른쪽 위 **Authorize** → 테스트 토큰을 붙여 넣고 **Authorize** → 이후 요청에 자동으로 붙습니다.
- curl 로는 `-H 'Authorization: Bearer <테스트 토큰>'`
- 토큰 값은 `src/main/resources/application-local.yml` 의 `app.auth.test-token` 에 있습니다.

테스트 토큰을 서버가 알아보게 하는 코드는 **이슈 #6·#20 에서 만듭니다.**
`application-local.yml` 에 `app.auth.test-token` 이 아직 없다면 로그인이 필요한 API 가 없는 단계이므로, 토큰 없이 부릅니다.

⚠️ 테스트 토큰은 배포 서버(prod)에서 통하면 안 됩니다. 배포 설정에는 넣지 않습니다.

## 5. 하지 않는 것

- 불러 보지 않고 "잘 됩니다" 라고 말하기
- 학생 대신 Swagger 까지 다 해 보고 끝내기 — 학생이 직접 눌러 보는 것이 목적입니다
- 일기·기분 같은 민감한 내용을 로그나 PR 본문에 그대로 붙이기 (CLAUDE.md 4장)
