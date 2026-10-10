---
description: 내가 만든 API 를 Swagger 화면에서 직접 불러 보며 잘 만들었는지 확인합니다
argument-hint: [확인할 API 주소나 기능 이름 (비워도 됩니다)]
---

확인할 것: **$ARGUMENTS** (비어 있으면 이 브랜치에서 새로 만들거나 고친 API 전부)

`.claude/skills/api-test/SKILL.md` 를 읽고 그 순서대로 진행하세요.

1. 서버를 (다시) 켭니다 — `./gradlew bootRun` (Windows 는 `.\gradlew.bat bootRun`)
2. Swagger 목록에 그 API 가 보이는지 확인합니다
3. 잘 되는 경우와 잘못 보낸 경우를 직접 불러 보고, 결과를 표로 보고합니다
4. **사용자가 직접** Swagger 에서 눌러 보게 한 단계씩 안내합니다
   👉 http://localhost:8080/swagger-ui.html

"요청", "응답", "200", "400" 같은 말이 처음인 사람도 있습니다.
결과를 말할 때는 **무슨 뜻인지 한 줄로 풀어서** 설명하고, 더 궁금해하면 `docs/API-TEST-GUIDE.md` 를 알려주세요.

문제가 있으면 고치기 전에 **무엇이 왜 틀렸는지부터** 설명합니다.
