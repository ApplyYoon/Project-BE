---
description: 코드에 문제가 없는지 검사를 한번에 돌립니다 (정리 + 빌드 + 테스트)
---

아래를 순서대로 실행하세요. (Windows 는 `./gradlew` 대신 `.\gradlew.bat`)

```bash
./gradlew spotlessApply
./gradlew spotlessCheck build
```

처음 실행은 Gradle 과 라이브러리를 내려받느라 몇 분 걸릴 수 있습니다. 기다리라고 알려주세요.

결과를 표로 간단히 보고하세요.

| 검사          | 결과 | 뜻                                   |
| ------------- | ---- | ------------------------------------ |
| spotlessApply | ✅   | 코드 모양 자동 정리 완료             |
| spotlessCheck | ✅   | 정리가 안 된 파일 없음               |
| build         | ✅   | 컴파일 성공 (문법·타입 에러 없음)    |
| test          | ✅   | 테스트 N개 전부 통과                 |

`spotlessApply` 가 파일을 고쳤다면 어떤 파일이 바뀌었는지 `git status --short` 로 확인해서 알려주세요.

실패한 것이 있으면:

1. **에러 메시지 원문**을 보여줍니다.
2. 그 에러가 **무슨 뜻인지 한국어 한 줄**로 풀어줍니다.
3. 어떻게 고칠지 제안하고, 간단한 것이면 바로 고칩니다.

테스트를 지우거나 `@Disabled` 로 꺼서 통과시키지 마세요. 설정을 끄거나 무시 주석으로 덮지도 마세요.

`Cannot find a Java installation ... languageVersion=21` 이 나오면 JDK 21 이 없는 것입니다.
`docs/TROUBLESHOOTING.md` 의 설치 방법을 안내하세요.
