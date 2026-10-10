---
description: 서버를 켜서 브라우저에서 API 응답을 확인합니다
---

```bash
./gradlew bootRun
```

(Windows 는 `.\gradlew.bat bootRun`)

이 명령은 서버가 켜져 있는 동안 **끝나지 않습니다.** 백그라운드로 실행하고,
`Started ProjectBeApplication` 이 로그에 보이면 켜진 것입니다.

켜진 뒤 아래를 알려주세요.

- 잘 켜졌는지 확인: http://localhost:8080/api/v1/health → `{"status":"ok","db":"connected"}`
- DB 들여다보기: http://localhost:8080/h2-console
  (`JDBC URL` 에 `jdbc:h2:mem:projectbe`, `User Name` 에 `sa`, 비밀번호는 비움)
- 처음 한 번은 라이브러리를 내려받느라 몇 분 걸립니다.
- 진행률이 `80% EXECUTING` 에서 멈춘 것처럼 보여도 정상입니다.
- 코드를 고친 뒤에는 **서버를 껐다가 다시 켜야** 반영됩니다.
- 서버를 끄려면 켠 터미널에서 `Ctrl + C`.
- 로컬 DB(H2)는 **서버를 끄면 데이터가 사라집니다.** 연습용이라 그렇습니다.

막혔을 때:

- `Port 8080 was already in use` → 이미 켜져 있다는 뜻입니다. 브라우저에서 주소만 확인하라고 안내하세요.
- `Cannot find a Java installation ... languageVersion=21` → JDK 21 이 없습니다. `/setup` 을 안내하세요.

**API 를 새로 만들었다면 직접 불러 보라고 알려주세요.** 예:

```bash
curl http://localhost:8080/api/v1/health
```
